package dev.saseq.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AttachmentSupportTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesReadableFileInsideConfiguredRoot() throws IOException {
        Path root = Files.createDirectory(tempDir.resolve("outbox"));
        Path file = Files.writeString(root.resolve("report.txt"), "hello");

        AttachmentSupport support = new AttachmentSupport(root.toString());

        assertEquals(file.toRealPath(), support.resolveReadableFile(file.toString()));
    }

    @Test
    void rejectsBlankPath() throws IOException {
        Path root = Files.createDirectory(tempDir.resolve("outbox"));

        AttachmentSupport support = new AttachmentSupport(root.toString());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> support.resolveReadableFile(" ")
        );

        assertEquals("filePath cannot be null or blank", ex.getMessage());
    }

    @Test
    void rejectsRelativePath() throws IOException {
        Path root = Files.createDirectory(tempDir.resolve("outbox"));

        AttachmentSupport support = new AttachmentSupport(root.toString());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> support.resolveReadableFile("report.txt")
        );

        assertEquals("filePath must be absolute", ex.getMessage());
    }

    @Test
    void rejectsFileOutsideConfiguredRoot() throws IOException {
        Path root = Files.createDirectory(tempDir.resolve("outbox"));
        Path outside = Files.writeString(tempDir.resolve("secret.txt"), "secret");

        AttachmentSupport support = new AttachmentSupport(root.toString());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> support.resolveReadableFile(outside.toString())
        );

        assertEquals("filePath must be inside DISCORD_FILE_ROOT", ex.getMessage());
    }

    @Test
    void rejectsDirectory() throws IOException {
        Path root = Files.createDirectory(tempDir.resolve("outbox"));
        Path directory = Files.createDirectory(root.resolve("folder"));

        AttachmentSupport support = new AttachmentSupport(root.toString());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> support.resolveReadableFile(directory.toString())
        );

        assertEquals("filePath must point to a regular file", ex.getMessage());
    }

    @Test
    void rejectsMissingFile() throws IOException {
        Path root = Files.createDirectory(tempDir.resolve("outbox"));
        Path missing = root.resolve("missing.zip");

        AttachmentSupport support = new AttachmentSupport(root.toString());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> support.resolveReadableFile(missing.toString())
        );

        assertEquals("filePath does not exist or cannot be resolved", ex.getMessage());
    }

    @Test
    void rejectsSymlinkEscape() throws IOException {
        Path root = Files.createDirectory(tempDir.resolve("outbox"));
        Path outside = Files.writeString(tempDir.resolve("outside.txt"), "secret");
        Path link = root.resolve("link.txt");

        Files.createSymbolicLink(link, outside);

        AttachmentSupport support = new AttachmentSupport(root.toString());

        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> support.resolveReadableFile(link.toString())
        );

        assertEquals("filePath must be inside DISCORD_FILE_ROOT", thrown.getMessage());
    }
}
