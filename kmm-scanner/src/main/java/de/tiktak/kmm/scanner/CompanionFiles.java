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
 */
@EqualsAndHashCode
@ToString
public final class CompanionFiles {

    static final String NFO_EXTENSION = ".nfo";
    static final String FOLDER_NFO = "movie.nfo";
    private static final char EXTENSION_SEPARATOR = '.';

    private final Path base;
    private final Path movieDirectory;

    private CompanionFiles(Path base, Path movieDirectory) {
        this.base = base;
        this.movieDirectory = movieDirectory;
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
        Path movieDirectory = format == DiscFormat.FILE ? directory : directory.getParent();
        return new CompanionFiles(directory.resolve(baseName), movieDirectory);
    }

    /** Die NFO, die KMM neu anlegt. */
    public Path nfoFile() {
        return withSuffix(NFO_EXTENSION);
    }

    /** Mögliche vorhandene NFOs in der Reihenfolge, in der sie gelesen werden. */
    public List<Path> nfoCandidates() {
        return List.of(nfoFile(), movieDirectory.resolve(FOLDER_NFO));
    }

    private Path withSuffix(String suffix) {
        return base.resolveSibling(base.getFileName() + suffix);
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
