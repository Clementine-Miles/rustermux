/*
 * Adapted from termux/termux-api-package, commit
 * 9e7f1531e1aa4a9c1e261a2dbde4de93653c366e.
 * Copyright (c) 2017-2021 Termux. MIT License, see assets/termux-api/LICENSE.
 *
 * The explicit Android broadcast carries the API arguments and two abstract
 * Unix addresses. The API connects to these to read stdin and write stdout.
 */
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <poll.h>
#include <pthread.h>
#include <signal.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/un.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

#ifndef PREFIX
#define PREFIX "/data/data/com.termux/files/usr"
#endif
#ifndef API_ROOT
#define API_ROOT "/data/data/com.termux/files/apps/com.termux/termux-api"
#endif
#define API_RECEIVER "com.termux/com.termux.api.TermuxApiReceiver"
#define API_VERSION "0.60.0"
#define DEFAULT_TIMEOUT_SECONDS 120
#define SOCKET_NAME_SIZE 48

struct input_forwarder {
    int server;
    int timeout;
};

static int64_t monotonic_millis(void) {
    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    return (int64_t) now.tv_sec * 1000 + now.tv_nsec / 1000000;
}

static int get_timeout(void) {
    const char *setting = getenv("TERMUX_API_TIMEOUT");
    if (!setting || *setting == '\0') return DEFAULT_TIMEOUT_SECONDS * 1000;

    char *end;
    errno = 0;
    long seconds = strtol(setting, &end, 10);
    if (errno || *end != '\0' || seconds < 0 || seconds > INT_MAX / 1000) {
        fputs("termux-api: TERMUX_API_TIMEOUT must be a non-negative number of seconds\n", stderr);
        exit(EXIT_FAILURE);
    }
    return seconds == 0 ? -1 : (int) seconds * 1000;
}

static int listen_on_socket(char name[SOCKET_NAME_SIZE]) {
    snprintf(name, SOCKET_NAME_SIZE, "termux-api.%08x%08x%08x%08x",
             arc4random(), arc4random(), arc4random(), arc4random());
    struct sockaddr_un address = {.sun_family = AF_UNIX};
    size_t size = strlen(name);
    memcpy(address.sun_path + 1, name, size);
    socklen_t address_length = (socklen_t) (offsetof(struct sockaddr_un, sun_path) + 1 + size);
    int server = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (server == -1) return -1;
    if (bind(server, (struct sockaddr *) &address, address_length) == 0 &&
        listen(server, 4) == 0) return server;

    int error = errno;
    close(server);
    errno = error;
    return -1;
}

static bool broadcast_failed(pid_t *child) {
    if (!child || *child <= 0) return false;

    int status;
    pid_t result = waitpid(*child, &status, WNOHANG);
    if (result <= 0) return false;
    *child = 0;
    return !WIFEXITED(status) || WEXITSTATUS(status) != 0;
}

static int accept_from_app(int server, int timeout, pid_t *broadcast) {
    struct pollfd descriptor = {.fd = server, .events = POLLIN};
    int64_t deadline = monotonic_millis() + timeout;
    for (;;) {
        int64_t remaining = deadline - monotonic_millis();
        if (timeout >= 0 && remaining <= 0) {
            errno = ETIMEDOUT;
            return -1;
        }

        // A failure to dispatch the broadcast should surface immediately.
        int wait = timeout < 0 ? -1 : (int) remaining;
        if (broadcast && *broadcast > 0 && (wait < 0 || wait > 250)) wait = 250;
        int result = poll(&descriptor, 1, wait);
        if (result < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        if (result == 0) {
            if (broadcast_failed(broadcast)) {
                errno = ECOMM;
                return -1;
            }
            continue;
        }

        int connection = accept4(server, NULL, NULL, SOCK_CLOEXEC);
        if (connection < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        struct ucred credentials;
        socklen_t size = sizeof(credentials);
        if (getsockopt(connection, SOL_SOCKET, SO_PEERCRED, &credentials, &size) == 0 &&
            credentials.uid == getuid()) return connection;
        close(connection);
    }
}

static void *forward_input(void *argument) {
    const struct input_forwarder *input = argument;
    int connection = accept_from_app(input->server, input->timeout, NULL);
    close(input->server);
    if (connection < 0) return NULL;

    char buffer[4096];
    for (;;) {
        ssize_t received = read(STDIN_FILENO, buffer, sizeof(buffer));
        if (received < 0 && errno == EINTR) continue;
        if (received <= 0) break;

        ssize_t written = 0;
        while (written < received) {
            ssize_t sent = send(connection, buffer + written, (size_t) (received - written), MSG_NOSIGNAL);
            if (sent < 0 && errno == EINTR) continue;
            if (sent <= 0) goto done;
            written += sent;
        }
    }

done:
    close(connection);
    return NULL;
}

static int write_stdout(const char *buffer, size_t length) {
    while (length > 0) {
        ssize_t written = write(STDOUT_FILENO, buffer, length);
        if (written < 0 && errno == EINTR) continue;
        if (written <= 0) return -1;
        buffer += written;
        length -= (size_t) written;
    }
    return 0;
}

static int forward_output(int connection, int timeout, int *descriptor) {
    struct pollfd socket_status = {.fd = connection, .events = POLLIN};
    for (;;) {
        int ready = poll(&socket_status, 1, timeout);
        if (ready < 0 && errno == EINTR) continue;
        if (ready <= 0) {
            if (ready == 0) errno = ETIMEDOUT;
            return -1;
        }

        char buffer[4096];
        union {
            struct cmsghdr aligned;
            char data[CMSG_SPACE(sizeof(int))];
        } control;
        struct iovec vector = {.iov_base = buffer, .iov_len = sizeof(buffer)};
        struct msghdr message = {
            .msg_iov = &vector,
            .msg_iovlen = 1,
            .msg_control = control.data,
            .msg_controllen = sizeof(control.data),
        };
        ssize_t length = recvmsg(connection, &message, 0);
        if (length < 0 && errno == EINTR) continue;
        if (length <= 0) return (int) length;

        bool received_descriptor = false;
        for (struct cmsghdr *data = CMSG_FIRSTHDR(&message); data;
             data = CMSG_NXTHDR(&message, data)) {
            if (data->cmsg_level != SOL_SOCKET || data->cmsg_type != SCM_RIGHTS ||
                data->cmsg_len < CMSG_LEN(sizeof(int))) continue;
            size_t count = (data->cmsg_len - CMSG_LEN(0)) / sizeof(int);
            const unsigned char *values = CMSG_DATA(data);
            for (size_t index = 0; index < count; ++index) {
                if (*descriptor >= 0) close(*descriptor);
                memcpy(descriptor, values + index * sizeof(int), sizeof(int));
                received_descriptor = true;
            }
        }

        // SCM_RIGHTS must accompany data. The API sends '@' when there is no stdout data.
        if (received_descriptor && length == 1 && buffer[0] == '@') continue;
        if (write_stdout(buffer, (size_t) length) < 0) return -1;
    }
}

static int read_process_starttime(void) {
    FILE *file = fopen("/proc/self/stat", "r");
    if (!file) return -1;

    char buffer[2048];
    char *line = fgets(buffer, sizeof(buffer), file);
    fclose(file);
    char *field = line ? strrchr(line, ')') : NULL;
    if (!field) return -1;

    // The closing ')' of the executable name precedes field 3. Read field 22.
    for (int index = 3; index <= 22; ++index) {
        field = strchr(field, ' ');
        if (!field) return -1;
        ++field;
    }
    unsigned long long ticks = strtoull(field, NULL, 10);
    return ticks <= INT_MAX ? (int) ticks : -1;
}

static _Noreturn void send_broadcast(int argc, char **argv, const char *input_socket,
                                    const char *output_socket, pid_t pid, int starttime) {
    int null = open("/dev/null", O_RDWR | O_CLOEXEC);
    if (null < 0 || dup2(null, STDIN_FILENO) < 0 || dup2(null, STDOUT_FILENO) < 0) {
        perror("termux-api: redirecting am");
        _exit(EXIT_FAILURE);
    }
    if (null > STDERR_FILENO) close(null);

    char pid_string[24];
    char uid_string[24];
    char starttime_string[24];
    snprintf(pid_string, sizeof(pid_string), "%ld", (long) pid);
    snprintf(uid_string, sizeof(uid_string), "%lu", (unsigned long) getuid());
    snprintf(starttime_string, sizeof(starttime_string), "%d", starttime);
    char *base[] = {
        "am", "broadcast", "--user", "current", "-n", API_RECEIVER,
        "--es", "socket_input", (char *) input_socket,
        "--es", "socket_output", (char *) output_socket,
        "--ei", "api_server_pid", pid_string,
        "--ei", "api_server_uid", uid_string,
        "--ei", "api_server_starttime", starttime_string,
        "--es", "api_method",
    };
    size_t base_count = sizeof(base) / sizeof(base[0]);
    char **arguments = calloc(base_count + (size_t) argc, sizeof(*arguments));
    if (!arguments) {
        perror("termux-api: allocating am arguments");
        _exit(EXIT_FAILURE);
    }
    memcpy(arguments, base, sizeof(base));
    memcpy(arguments + base_count, argv + 1, (size_t) (argc - 1) * sizeof(*argv));
    // The socket bridge dispatches from the app itself. Direct app_process
    // startup can be blocked by Android's seccomp policy in a Termux session.
    const char *socket_enabled = getenv("TERMUX_APP__AM_SOCKET_SERVER_ENABLED");
    if (socket_enabled && strcmp(socket_enabled, "true") == 0) {
        arguments[0] = "termux-am";
        execv(PREFIX "/bin/termux-am", arguments);
        arguments[0] = "am";
    }
    execv(PREFIX "/bin/am", arguments);
    perror("termux-api: executing am");
    _exit(EXIT_FAILURE);
}

static _Noreturn void exec_callback(int descriptor) {
    char number[24];
    snprintf(number, sizeof(number), "%d", descriptor);
    const char *export_fd = getenv("TERMUX_EXPORT_FD");
    if (export_fd && strcmp(export_fd, "true") == 0) {
        if (setenv("TERMUX_USB_FD", number, 1) == 0) {
            execl(PREFIX "/bin/bash", "bash", API_ROOT "/libexec/termux-callback", NULL);
        }
    } else {
        execl(PREFIX "/bin/bash", "bash", API_ROOT "/libexec/termux-callback", number, NULL);
    }
    perror("termux-api: executing USB callback");
    exit(EXIT_FAILURE);
}

int main(int argc, char **argv) {
    if (argc == 2 && strcmp(argv[1], "--version") == 0) {
        puts(API_VERSION);
        return EXIT_SUCCESS;
    }
    if (argc < 2 || argv[1][0] == '\0') {
        fputs("usage: termux-api API_METHOD [ANDROID_INTENT_OPTIONS...]\n", stderr);
        return EXIT_FAILURE;
    }

    int timeout = get_timeout();
    char output_socket[SOCKET_NAME_SIZE];
    char input_socket[SOCKET_NAME_SIZE];
    int output_server = listen_on_socket(output_socket);
    int input_server = output_server < 0 ? -1 : listen_on_socket(input_socket);
    if (output_server < 0 || input_server < 0) {
        perror("termux-api: opening response sockets");
        return EXIT_FAILURE;
    }

    pid_t client = getpid();
    int starttime = read_process_starttime();
    pid_t broadcast = fork();
    if (broadcast < 0) {
        perror("termux-api: starting broadcast");
        return EXIT_FAILURE;
    }
    if (broadcast == 0) send_broadcast(argc, argv, input_socket, output_socket, client, starttime);

    struct input_forwarder input = {.server = input_server, .timeout = timeout};
    pthread_t writer;
    int started = pthread_create(&writer, NULL, forward_input, &input);
    if (started != 0) {
        errno = started;
        perror("termux-api: starting stdin forwarding");
        kill(broadcast, SIGTERM);
        return EXIT_FAILURE;
    }

    int connection = accept_from_app(output_server, timeout, &broadcast);
    close(output_server);
    if (connection < 0) {
        perror("termux-api: waiting for Android API");
        if (broadcast > 0) kill(broadcast, SIGTERM);
        return EXIT_FAILURE;
    }

    int descriptor = -1;
    int result = forward_output(connection, timeout, &descriptor);
    close(connection);
    if (result < 0) {
        perror("termux-api: reading Android API response");
        if (broadcast > 0) kill(broadcast, SIGTERM);
        return EXIT_FAILURE;
    }
    if (descriptor >= 0) exec_callback(descriptor);
    return EXIT_SUCCESS;
}
