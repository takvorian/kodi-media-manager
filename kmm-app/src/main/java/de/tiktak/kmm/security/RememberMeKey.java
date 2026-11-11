package de.tiktak.kmm.security;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * The secret that signs the "stay signed in" cookies. It is created once and kept in the KMM home directory, so that
 * the cookies survive a restart or an update; whoever deletes the file signs everybody out.
 */
final class RememberMeKey {

    static final String FILE = "security/remember-me.key";
    private static final int KEY_BYTES = 32;

    private RememberMeKey() {
    }

    static String readOrCreate(Path home) {
        Path file = home.resolve(FILE);
        try {
            if (Files.isRegularFile(file)) {
                String key = Files.readString(file, StandardCharsets.UTF_8).strip();
                if (!key.isEmpty()) {
                    return key;
                }
            }
            byte[] bytes = new byte[KEY_BYTES];
            new SecureRandom().nextBytes(bytes);
            String key = HexFormat.of().formatHex(bytes);
            Files.createDirectories(file.getParent());
            Files.writeString(file, key, StandardCharsets.UTF_8);
            return key;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read or create " + file, e);
        }
    }
}
