package com.termux.shared.file;

import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

@RequiresApi(26)
final class RecursiveFileDelete {
    private RecursiveFileDelete() {}

    static void delete(Path path, boolean keepRoot) throws IOException {
        Path absolutePath = path.toAbsolutePath();
        Path parent = absolutePath.getParent();
        if (parent == null && !keepRoot) {
            throw new FileSystemException(absolutePath.toString(), null, "Cannot delete the filesystem root");
        }

        List<IOException> failures = new ArrayList<>();
        // Use descriptor-relative operations with NOFOLLOW_LINKS when the filesystem supports them.
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent == null ? absolutePath : parent)) {
            if (stream instanceof SecureDirectoryStream) {
                SecureDirectoryStream<Path> directory = (SecureDirectoryStream<Path>) stream;
                if (parent == null) {
                    deleteEntries(directory, failures);
                } else if (keepRoot) {
                    try (SecureDirectoryStream<Path> root =
                             directory.newDirectoryStream(absolutePath.getFileName(), LinkOption.NOFOLLOW_LINKS)) {
                        deleteEntries(root, failures);
                    }
                } else {
                    deleteEntry(directory, absolutePath.getFileName(), failures);
                }
            } else {
                // The default file tree walk treats symbolic links as files.
                deleteTree(absolutePath, keepRoot, failures);
            }
        }
        if (!failures.isEmpty()) {
            IOException error = new FileSystemException(absolutePath.toString(), null, "Failed to delete all entries");
            for (IOException failure : failures) {
                error.addSuppressed(failure);
            }
            throw error;
        }
    }

    private static void deleteEntries(SecureDirectoryStream<Path> directory, List<IOException> failures) {
        try {
            for (Path entry : directory) {
                deleteEntry(directory, entry.getFileName(), failures);
            }
        } catch (DirectoryIteratorException e) {
            failures.add(e.getCause());
        }
    }

    private static void deleteEntry(SecureDirectoryStream<Path> parent, Path name, List<IOException> failures) {
        try {
            BasicFileAttributes attributes = parent.getFileAttributeView(
                name, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).readAttributes();
            if (attributes.isDirectory()) {
                int failuresBefore = failures.size();
                try (SecureDirectoryStream<Path> directory = parent.newDirectoryStream(name, LinkOption.NOFOLLOW_LINKS)) {
                    deleteEntries(directory, failures);
                }
                if (failures.size() == failuresBefore) parent.deleteDirectory(name);
            } else {
                parent.deleteFile(name);
            }
        } catch (IOException e) {
            failures.add(e);
        }
    }

    private static void deleteTree(Path root, boolean keepRoot, List<IOException> failures) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (keepRoot && file.equals(root)) {
                    failures.add(new NotDirectoryException(file.toString()));
                } else {
                    delete(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException error) {
                failures.add(error);
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
                    Files.delete(path);
                } catch (IOException e) {
                    failures.add(e);
                }
            }
        });
    }
}
