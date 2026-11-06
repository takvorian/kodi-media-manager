package de.tiktak.kmm.movie;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 of a file path as a hex string. Serves as a unique key because a
 * unique index over the full path exceeds the index limits of some databases.
 */
public final class FilePathHash {

    private static final String ALGORITHM = "SHA-256";

    private FilePathHash() {
    }

    public static String of(String filePath) {
        try {
            byte[] digest = MessageDigest.getInstance(ALGORITHM).digest(filePath.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // Every Java platform must provide SHA-256.
            throw new IllegalStateException(e);
        }
    }
}
