package de.tiktak.kmm.tvshow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 eines Pfads als Hex-Zeichenkette. Dient als Schlüssel, weil ein Index über den vollständigen
 * Pfad die Indexgrenzen mancher Datenbanken überschreitet.
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
            // SHA-256 muss jede Java-Plattform bereitstellen.
            throw new IllegalStateException(e);
        }
    }
}
