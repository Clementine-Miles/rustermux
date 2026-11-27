package com.termux.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;

/** Updates the links owned by termux-setup-storage and leaves other entries intact. */
final class StorageLinks {

    private StorageLinks() {}

    static synchronized void update(Path directory, Map<String, Path> links) throws IOException {
        ensureDirectory(directory.getParent());
        ensureDirectory(directory);
        for (String name : links.keySet()) {
            Path link = directory.resolve(name);
            if (!directory.equals(link.getParent()) || name.equals(".") || name.equals("..")) {
                throw new IOException("Invalid storage link name: " + name);
            }
            existingLinkTarget(link);
        }

        for (Map.Entry<String, Path> entry : links.entrySet()) {
            Path link = directory.resolve(entry.getKey());
            Path target = entry.getValue();
            Path oldTarget = existingLinkTarget(link);
            if (oldTarget == null) {
                Files.createSymbolicLink(link, target);
            } else if (!oldTarget.equals(target)) {
                replaceLink(directory, link, target);
            }
        }
    }

    private static void ensureDirectory(Path path) throws IOException {
        try {
            if (!Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isDirectory()) {
                throw new IOException("Move " + path + " and run termux-setup-storage again.");
            }
        } catch (NoSuchFileException e) {
            Files.createDirectory(path);
        }
    }

    private static Path existingLinkTarget(Path path) throws IOException {
        try {
            if (!Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isSymbolicLink()) {
                throw new IOException("Move " + path + " out of ~/storage and run termux-setup-storage again.");
            }
            return Files.readSymbolicLink(path);
        } catch (NoSuchFileException e) {
            return null;
        }
    }

    private static void replaceLink(Path directory, Path link, Path target) throws IOException {
        Path staging = Files.createTempDirectory(directory, ".storage-links-");
        Path replacement = staging.resolve("link");
        try {
            Files.createSymbolicLink(replacement, target);
            if (existingLinkTarget(link) == null) Files.createSymbolicLink(link, target);
            else Files.move(replacement, link, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(replacement);
            Files.delete(staging);
        }
    }
}
