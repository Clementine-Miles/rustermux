#!/system/bin/sh

banner="${PREFIX}/etc/motd"
[ -r "$banner" ] || exit 0

while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in
        " - X11:"*"pkg install x11-repo") continue ;;
    esac
    printf '%s\n' "$line"
done < "$banner"
