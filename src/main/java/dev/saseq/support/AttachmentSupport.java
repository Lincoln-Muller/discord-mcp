package dev.saseq.support;

import net.dv8tion.jda.api.utils.FileUpload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public final class AttachmentSupport {

    private final Path configuredRoot;

    public AttachmentSupport(String configuredRoot) {
        if (configuredRoot == null || configuredRoot.isBlank()) {
            throw new IllegalArgumentException("configuredRoot cannot be null or blank");
        }
        this.configuredRoot = Path.of(configuredRoot).toAbsolutePath().normalize();
    }

    public Path resolveReadableFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath cannot be null or blank");
        }

        final Path candidate;

        try {
            candidate = Path.of(filePath);
        } catch (InvalidPathException ex) {
            throw new IllegalArgumentException("filePath is invalid");
        }

        if (!candidate.isAbsolute()) {
            throw new IllegalArgumentException("filePath must be absolute");
        }

        final Path realRoot;
        final Path realCandidate;

        try {
            realRoot = configuredRoot.toRealPath();
            realCandidate = candidate.normalize().toRealPath();
        } catch (IOException ex) {
            throw new IllegalArgumentException("filePath does not exist or cannot be resolved");
        }

        if (!realCandidate.startsWith(realRoot)) {
            throw new IllegalArgumentException(
                    "filePath must be inside DISCORD_FILE_ROOT"
            );
        }

        if (!Files.isRegularFile(realCandidate)) {
            throw new IllegalArgumentException(
                    "filePath must point to a regular file"
            );
        }

        if (!Files.isReadable(realCandidate)) {
            throw new IllegalArgumentException(
                    "filePath is not readable"
            );
        }

        return realCandidate;
    }

    public FileUpload createUpload(String filePath) {
        return FileUpload.fromData(resolveReadableFile(filePath));
    }
}
