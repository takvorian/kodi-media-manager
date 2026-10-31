package de.tiktak.kmm.export;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.core.io.FileSystemResource;

/**
 * Eine temporäre Datei, die nach dem Lesen gelöscht wird.
 */
public class TemporaryFileResource extends FileSystemResource {

    private final Path file;

    public TemporaryFileResource(Path file) {
        super(file);
        this.file = file;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new FilterInputStream(super.getInputStream()) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    Files.deleteIfExists(file);
                }
            }
        };
    }
}
