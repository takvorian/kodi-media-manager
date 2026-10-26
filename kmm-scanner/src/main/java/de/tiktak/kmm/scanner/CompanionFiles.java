package de.tiktak.kmm.scanner;

import java.nio.file.Path;
import java.util.ArrayList;
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
 * Kodi akzeptiert außerdem eine {@code movie.nfo} im Filmordner und bei Stapeln Dateien mit dem Namen
 * des ersten Teils ({@code Heat-cd1.nfo}, so schreibt sie Ember); beides wird beim Lesen berücksichtigt.
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
    /** Name des ersten Teils ohne Endung bei Stapeln, sonst {@code null}. */
    private final String firstPartName;
    private final Path movieDirectory;
    private final boolean disc;

    private CompanionFiles(Path directory, String baseName, String firstPartName, Path movieDirectory,
            boolean disc) {
        this.directory = directory;
        this.baseName = baseName;
        this.firstPartName = firstPartName;
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
        String nameWithoutExtension = nameWithoutExtension(mainFile);
        String baseName = baseName(mainFile, nameWithoutExtension, partCount > 1);
        String firstPartName = baseName.equals(nameWithoutExtension) ? null : nameWithoutExtension;
        boolean disc = format != DiscFormat.FILE;
        Path movieDirectory = disc ? directory.getParent() : directory;
        return new CompanionFiles(directory, baseName, firstPartName, movieDirectory, disc);
    }

    /** Die NFO, die KMM neu anlegt. */
    public Path nfoFile() {
        return withSuffix(NFO_EXTENSION);
    }

    /** Mögliche vorhandene NFOs in der Reihenfolge, in der sie gelesen werden. */
    public List<Path> nfoCandidates() {
        List<Path> candidates = new ArrayList<>();
        candidates.add(nfoFile());
        if (firstPartName != null) {
            candidates.add(directory.resolve(firstPartName + NFO_EXTENSION));
        }
        candidates.add(movieDirectory.resolve(FOLDER_NFO));
        return List.copyOf(candidates);
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

    /**
     * Alle Dateien, unter denen ein vorhandenes Bild dieser Art liegen kann; zuerst die Namen, unter
     * denen KMM speichert, danach bei Stapeln die nach dem ersten Teil benannten.
     */
    public List<Path> artworkCandidates(String kodiName) {
        List<Path> candidates = new ArrayList<>();
        ARTWORK_EXTENSIONS.forEach(extension -> candidates.add(artworkFile(kodiName, extension)));
        if (firstPartName != null && !disc) {
            ARTWORK_EXTENSIONS.forEach(extension -> candidates.add(directory.resolve(
                    firstPartName + ARTWORK_SEPARATOR + kodiName + EXTENSION_SEPARATOR + extension)));
        }
        return List.copyOf(candidates);
    }

    private Path withSuffix(String suffix) {
        return directory.resolve(baseName + suffix);
    }

    private static String nameWithoutExtension(Path mainFile) {
        String fileName = mainFile.getFileName().toString();
        int extensionStart = fileName.lastIndexOf(EXTENSION_SEPARATOR);
        return extensionStart > 0 ? fileName.substring(0, extensionStart) : fileName;
    }

    private static String baseName(Path mainFile, String nameWithoutExtension, boolean stacked) {
        if (!stacked) {
            return nameWithoutExtension;
        }
        String fileName = mainFile.getFileName().toString();
        int extensionStart = fileName.lastIndexOf(EXTENSION_SEPARATOR);
        String extension = extensionStart > 0 ? fileName.substring(extensionStart + 1) : "";
        return StackedFile.of(mainFile, nameWithoutExtension, extension)
                .map(StackedFile::baseName)
                .orElse(nameWithoutExtension);
    }
}
