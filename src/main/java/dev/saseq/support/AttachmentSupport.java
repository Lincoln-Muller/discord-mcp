package dev.saseq.support;

import net.dv8tion.jda.api.utils.FileUpload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class AttachmentSupport {

    private final Path configuredRoot;
    private final Object configuredRootFileKey;

    public AttachmentSupport(String configuredRoot) {
        if (configuredRoot == null || configuredRoot.isBlank()) {
            throw new IllegalArgumentException("configuredRoot cannot be null or blank");
        }
        try {
            this.configuredRoot = Path.of(configuredRoot).toRealPath();
            this.configuredRootFileKey = Files.readAttributes(
                    this.configuredRoot,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS
            ).fileKey();
        } catch (IOException | InvalidPathException ex) {
            throw new IllegalArgumentException("configuredRoot does not exist or cannot be resolved");
        }

        if (configuredRootFileKey == null) {
            throw new IllegalArgumentException("configuredRoot cannot be opened securely");
        }
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

        final Path realCandidate;

        try {
            realCandidate = candidate.normalize().toRealPath();
        } catch (IOException ex) {
            throw new IllegalArgumentException("filePath does not exist or cannot be resolved");
        }

        if (!realCandidate.startsWith(configuredRoot)) {
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
        Path realCandidate = resolveReadableFile(filePath);
        Path relative = configuredRoot.relativize(realCandidate);
        List<SecureDirectoryStream<Path>> openedDirectories = new ArrayList<>();

        try (DirectoryStream<Path> rootStream = Files.newDirectoryStream(configuredRoot)) {
            if (!(rootStream instanceof SecureDirectoryStream<Path> secureRoot)) {
                throw new IllegalArgumentException("configuredRoot cannot be opened securely");
            }

            BasicFileAttributeView rootAttributes = secureRoot.getFileAttributeView(
                    BasicFileAttributeView.class
            );
            if (rootAttributes == null || !Objects.equals(
                    configuredRootFileKey,
                    rootAttributes.readAttributes().fileKey()
            )) {
                throw new IllegalArgumentException("configuredRoot changed or cannot be opened securely");
            }

            SecureDirectoryStream<Path> current = secureRoot;
            Path parent = relative.getParent();
            if (parent != null) {
                for (Path component : parent) {
                    current = current.newDirectoryStream(component, LinkOption.NOFOLLOW_LINKS);
                    openedDirectories.add(current);
                }
            }

            Set<OpenOption> options = Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
            SeekableByteChannel channel = current.newByteChannel(relative.getFileName(), options);
            InputStream data = Channels.newInputStream(channel);

            try {
                return FileUpload.fromData(data, realCandidate.getFileName().toString());
            } catch (RuntimeException ex) {
                data.close();
                throw ex;
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("filePath does not exist or cannot be resolved");
        } finally {
            for (int index = openedDirectories.size() - 1; index >= 0; index--) {
                try {
                    openedDirectories.get(index).close();
                } catch (IOException ignored) {
                    // The upload owns the independently opened file channel.
                }
            }
        }
    }
}
