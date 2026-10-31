package de.tiktak.kmm.movie;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 eines Dateipfads als Hex-Zeichenkette. Dient als eindeutiger Schlüssel, weil ein
 * Unique-Index über den vollständigen Pfad die Indexgrenzen mancher Datenbanken überschreitet.
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
            // SHA-256 muss jede Java-Plattform bereitstellen.
            throw new IllegalStateException(e);
        }
    }
}
