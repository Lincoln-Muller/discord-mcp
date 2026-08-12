package dev.saseq.support;

import net.dv8tion.jda.api.utils.FileUpload;
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
    void resolvesReadableFileInsideNestedDirectory() throws IOException {
        Path root = Files.createDirectory(tempDir.resolve("outbox"));
        Path nested = Files.createDirectories(root.resolve("reports/daily"));
        Path file = Files.writeString(nested.resolve("report.txt"), "hello");

        AttachmentSupport support = new AttachmentSupport(root.toString());

        assertEquals(file.toRealPath(), support.resolveReadableFile(file.toString()));
    }

    @Test
    void rejectsNullPath() throws IOException {
        Path root = Files.createDirectory(tempDir.resolve("outbox"));

        AttachmentSupport support = new AttachmentSupport(root.toString());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> support.resolveReadableFile(null)
        );

        assertEquals("filePath cannot be null or blank", ex.getMessage());
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

    @Test
    void createsUploadFromReadableFile() throws IOException {
        Path root = Files.createDirectory(tempDir.resolve("outbox"));
        Path nested = Files.createDirectories(root.resolve("reports/daily"));
        Path file = Files.writeString(nested.resolve("report.txt"), "hello");

        AttachmentSupport support = new AttachmentSupport(root.toString());

        try (FileUpload upload = support.createUpload(file.toString())) {
            assertEquals("report.txt", upload.getName());
            assertArrayEquals("hello".getBytes(), upload.getData().readAllBytes());
        }
    }

    @Test
    void uploadKeepsOriginallyOpenedFileAfterPathIsReplaced() throws IOException {
        Path root = Files.createDirectory(tempDir.resolve("outbox"));
        Path file = Files.writeString(root.resolve("report.txt"), "inside");
        Path outside = Files.writeString(tempDir.resolve("outside.txt"), "outside");
        AttachmentSupport support = new AttachmentSupport(root.toString());

        try (FileUpload upload = support.createUpload(file.toString())) {
            Files.delete(file);
            Files.createSymbolicLink(file, outside);

            assertArrayEquals("inside".getBytes(), upload.getData().readAllBytes());
        }
    }

    @Test
    void createUploadRejectsReplacedConfiguredRoot() throws IOException {
        Path root = Files.createDirectory(tempDir.resolve("outbox"));
        Path requested = root.resolve("report.txt");
        Files.writeString(requested, "inside");
        AttachmentSupport support = new AttachmentSupport(root.toString());

        Path movedRoot = tempDir.resolve("moved-outbox");
        Files.move(root, movedRoot);
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Files.writeString(outside.resolve("report.txt"), "outside");
        Files.createSymbolicLink(root, outside);

        assertThrows(IllegalArgumentException.class, () -> support.createUpload(requested.toString()));
    }
}
