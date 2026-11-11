package de.tiktak.kmm.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RememberMeKeyTest {

    private static final int KEY_LENGTH = 64;

    @TempDir
    private Path home;

    @Test
    void createsARandomKeyOnTheFirstStart() throws IOException {
        String key = RememberMeKey.readOrCreate(home);

        assertThat(key).hasSize(KEY_LENGTH);
        assertThat(home.resolve(RememberMeKey.FILE)).hasContent(key);
        assertThat(RememberMeKey.readOrCreate(Files.createDirectory(home.resolve("other")))).isNotEqualTo(key);
    }

    @Test
    void keepsTheKeyAcrossRestarts() {
        String key = RememberMeKey.readOrCreate(home);

        assertThat(RememberMeKey.readOrCreate(home)).isEqualTo(key);
    }

    @Test
    void replacesAnEmptyKeyFile() throws IOException {
        Path file = home.resolve(RememberMeKey.FILE);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "  \n");

        assertThat(RememberMeKey.readOrCreate(home)).hasSize(KEY_LENGTH);
    }
}
