package com.termux.shared.file;

import com.termux.shared.termux.TermuxConstants;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

final class RecursiveFileDelete {
    private static final int MAX_OPEN_DIRECTORY_DEPTH = 256;

    private RecursiveFileDelete() {}

    /** Checks the target without resolving a symbolic link at its final component. */
    static Path checkedPath(Path path) throws IOException {
        Path absolutePath = path.toAbsolutePath();
        if (path.toString().isEmpty() || !absolutePath.equals(absolutePath.normalize()) || absolutePath.getParent() == null) {
            throw new FileSystemException(absolutePath.toString(), null, "Unsafe deletion path");
        }
        Path privateAppDirectory = path.getFileSystem().getPath(TermuxConstants.TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR_PATH);
        Path primaryUserDirectory = path.getFileSystem().getPath("/data/user/0").resolve(privateAppDirectory.getFileName());
        for (Path appDirectory : new Path[]{privateAppDirectory, primaryUserDirectory}) {
            Path files = appDirectory.resolve("files");
            if (files.resolve("home").startsWith(absolutePath) || files.resolve("usr").startsWith(absolutePath)) {
                throw new FileSystemException(absolutePath.toString(), null, "Protected Termux directory");
            }
        }

        // Find the nearest existing parent so a missing directory can still be safely created.
        for (Path parent = absolutePath.getParent(); parent != null; parent = parent.getParent()) {
            try {
                Path realParent = parent.toRealPath();
                if (!realParent.equals(parent) && !isPrimaryUserAlias(parent, realParent)) {
                    throw new FileSystemException(absolutePath.toString(), null, "Symbolic link in the parent path");
                }
                return realParent.resolve(parent.relativize(absolutePath));
            } catch (NoSuchFileException e) {
                // A dangling link is an unsafe parent even though it has no real path.
                if (Files.isSymbolicLink(parent)) {
                    throw new FileSystemException(absolutePath.toString(), null, "Dangling link in the parent path");
                }
            }
        }
        throw new NoSuchFileException(absolutePath.toString());
    }

    private static boolean isPrimaryUserAlias(Path path, Path realPath) {
        Path data = path.getFileSystem().getPath("/data/data");
        Path user = path.getFileSystem().getPath("/data/user/0");
        return (path.startsWith(data) && user.resolve(data.relativize(path)).equals(realPath))
            || (path.startsWith(user) && data.resolve(user.relativize(path)).equals(realPath));
    }

    static void delete(Path path, boolean keepRoot, boolean expectedDirectory) throws IOException {
        Path absolutePath = checkedPath(path);
        Path parent = absolutePath.getParent();

        List<IOException> failures = new ArrayList<>();
        // Use descriptor-relative operations with NOFOLLOW_LINKS when the filesystem supports them.
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent)) {
            if (stream instanceof SecureDirectoryStream) {
                SecureDirectoryStream<Path> directory = (SecureDirectoryStream<Path>) stream;
                if (keepRoot) {
                    try (SecureDirectoryStream<Path> root =
                             directory.newDirectoryStream(absolutePath.getFileName(), LinkOption.NOFOLLOW_LINKS)) {
                        deleteEntries(root, 0, failures);
                    }
                } else {
                    deleteEntry(directory, absolutePath.getFileName(), expectedDirectory, 0, failures);
                }
            } else {
                // The default file tree walk treats symbolic links as files.
                deleteTree(absolutePath, keepRoot, expectedDirectory, failures);
            }
        } catch (NoSuchFileException e) {
            if (keepRoot) throw e;
        }
        if (!failures.isEmpty()) {
            IOException error = new FileSystemException(absolutePath.toString(), null, "Failed to delete all entries");
            for (IOException failure : failures) {
                error.addSuppressed(failure);
            }
            throw error;
        }
    }

    private static void deleteEntries(SecureDirectoryStream<Path> directory, int depth, List<IOException> failures) {
        try {
            for (Path entry : directory) {
                deleteEntry(directory, entry.getFileName(), null, depth, failures);
            }
        } catch (DirectoryIteratorException e) {
            failures.add(e.getCause());
        }
    }

    private static void deleteEntry(SecureDirectoryStream<Path> parent, Path name, Boolean expectedDirectory, int depth,
                                    List<IOException> failures) {
        try {
            BasicFileAttributes attributes = parent.getFileAttributeView(
                name, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).readAttributes();
            if (expectedDirectory != null && attributes.isDirectory() != expectedDirectory) {
                throw new FileSystemException(name.toString(), null, "File type changed before deletion");
            }
            if (attributes.isDirectory()) {
                if (depth >= MAX_OPEN_DIRECTORY_DEPTH) {
                    throw new FileSystemException(name.toString(), null, "Directory tree is too deep to delete safely");
                }
                int failuresBefore = failures.size();
                try (SecureDirectoryStream<Path> directory = parent.newDirectoryStream(name, LinkOption.NOFOLLOW_LINKS)) {
                    deleteEntries(directory, depth + 1, failures);
                }
                if (failures.size() == failuresBefore) parent.deleteDirectory(name);
            } else {
                parent.deleteFile(name);
            }
        } catch (NoSuchFileException e) {
            // A concurrent deletion has already removed this entry.
        } catch (IOException e) {
            failures.add(e);
        }
    }

    private static void deleteTree(Path root, boolean keepRoot, boolean expectedDirectory,
                                   List<IOException> failures) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                if (directory.equals(root) && !expectedDirectory) {
                    throw new FileSystemException(directory.toString(), null, "File type changed before deletion");
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (expectedDirectory && file.equals(root)) {
                    failures.add(new NotDirectoryException(file.toString()));
                } else {
                    delete(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException error) {
                if (!(error instanceof NoSuchFileException) || (keepRoot && file.equals(root))) failures.add(error);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException error) {
                if (error != null) {
                    failures.add(error);
                } else if (!keepRoot || !directory.equals(root)) {
                    delete(directory);
                }
                return FileVisitResult.CONTINUE;
            }

            private void delete(Path path) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    failures.add(e);
                }
            }
        });
    }
}
