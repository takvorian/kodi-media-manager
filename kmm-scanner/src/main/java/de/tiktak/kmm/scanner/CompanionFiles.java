package de.tiktak.kmm.scanner;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Begleitdateien eines Films nach Kodi-Konvention (Kodi 19–21), abgeleitet von einem gemeinsamen
 * Basisnamen:
 * <ul>
 * <li>Einzeldatei {@code Heat.mkv} → {@code Heat.nfo}</li>
 * <li>Stapel {@code Heat-cd1.avi}, {@code Heat-cd2.avi} → {@code Heat.nfo}</li>
 * <li>DVD-Ordner → {@code VIDEO_TS/VIDEO_TS.nfo}</li>
 * <li>Blu-ray-Ordner → {@code BDMV/index.nfo}</li>
 * </ul>
 * Kodi akzeptiert außerdem eine {@code movie.nfo} im Filmordner; sie wird beim Lesen berücksichtigt.
 * <p>
 * Bilder heißen {@code <basisname>-poster.jpg}, {@code -fanart.jpg} usw.; bei DVD- und Blu-ray-Ordnern
 * liegen sie als {@code poster.jpg} usw. im Filmordner, wo Kodi sie bei Disc-Strukturen sucht.
 */
@EqualsAndHashCode
@ToString
public final class CompanionFiles {

    static final String NFO_EXTENSION = ".nfo";
    static final String FOLDER_NFO = "movie.nfo";
    /** Bildformate, die Kodi als lokales Artwork liest. */
    static final List<String> ARTWORK_EXTENSIONS = List.of("jpg", "jpeg", "png");
    private static final char EXTENSION_SEPARATOR = '.';
    private static final char ARTWORK_SEPARATOR = '-';

    /** Ordner der Hauptdatei. */
    private final Path directory;
    /**
     * Basisname als Text, nicht als Pfad: Windows erlaubt kein Leerzeichen am Ende eines Pfadteils,
     * wohl aber vor der Endung ({@code Film .mkv} → {@code Film .nfo}).
     */
    private final String baseName;
    private final Path movieDirectory;
    private final boolean disc;

    private CompanionFiles(Path directory, String baseName, Path movieDirectory, boolean disc) {
        this.directory = directory;
        this.baseName = baseName;
        this.movieDirectory = movieDirectory;
        this.disc = disc;
    }

    /**
     * @param mainFile  Hauptdatei des Films (bei Stapeln der erste Teil, bei Discs die Markerdatei)
     * @param format    Speicherform
     * @param partCount Anzahl der Teile; nur bei mehr als einem Teil wird die Teilangabe entfernt
     */
    public static CompanionFiles of(Path mainFile, DiscFormat format, int partCount) {
        Objects.requireNonNull(mainFile, "mainFile must not be null");
        Objects.requireNonNull(format, "format must not be null");
        Path directory = mainFile.toAbsolutePath().normalize().getParent();
        String baseName = baseName(mainFile, partCount > 1);
        boolean disc = format != DiscFormat.FILE;
        Path movieDirectory = disc ? directory.getParent() : directory;
        return new CompanionFiles(directory, baseName, movieDirectory, disc);
    }

    /** Die NFO, die KMM neu anlegt. */
    public Path nfoFile() {
        return withSuffix(NFO_EXTENSION);
    }

    /** Mögliche vorhandene NFOs in der Reihenfolge, in der sie gelesen werden. */
    public List<Path> nfoCandidates() {
        return List.of(nfoFile(), movieDirectory.resolve(FOLDER_NFO));
    }

    /**
     * Datei, unter der ein Bild gespeichert wird.
     *
     * @param kodiName  Kodi-Bezeichnung der Bildart, z. B. {@code poster}, {@code clearlogo}
     * @param extension Dateiendung ohne Punkt, z. B. {@code jpg}
     */
    public Path artworkFile(String kodiName, String extension) {
        String fileName = kodiName + EXTENSION_SEPARATOR + extension;
        return disc ? movieDirectory.resolve(fileName) : withSuffix(ARTWORK_SEPARATOR + fileName);
    }

    /** Alle Dateien, unter denen ein vorhandenes Bild dieser Art liegen kann. */
    public List<Path> artworkCandidates(String kodiName) {
        return ARTWORK_EXTENSIONS.stream().map(extension -> artworkFile(kodiName, extension)).toList();
    }

    private Path withSuffix(String suffix) {
        return directory.resolve(baseName + suffix);
    }

    private static String baseName(Path mainFile, boolean stacked) {
        String fileName = mainFile.getFileName().toString();
        int extensionStart = fileName.lastIndexOf(EXTENSION_SEPARATOR);
        String nameWithoutExtension = extensionStart > 0 ? fileName.substring(0, extensionStart) : fileName;
        if (!stacked) {
            return nameWithoutExtension;
        }
        String extension = extensionStart > 0 ? fileName.substring(extensionStart + 1) : "";
        return StackedFile.of(mainFile, nameWithoutExtension, extension)
                .map(StackedFile::baseName)
                .orElse(nameWithoutExtension);
    }
}
