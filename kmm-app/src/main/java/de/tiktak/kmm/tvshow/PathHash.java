package de.tiktak.kmm.tvshow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 of a path as a hex string. Serves as a key because an index over the full
 * path exceeds the index limits of some databases.
 */
final class PathHash {

    private static final String ALGORITHM = "SHA-256";

    private PathHash() {
    }

    static String of(String path) {
        try {
            byte[] digest = MessageDigest.getInstance(ALGORITHM).digest(path.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // Every Java platform must provide SHA-256.
            throw new IllegalStateException(e);
        }
    }
}
