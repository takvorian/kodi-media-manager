package de.tiktak.kmm.scanner;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Companion files of a movie according to Kodi's convention (Kodi 19–21), derived from a common base name:
 * <ul>
 * <li>single file {@code Heat.mkv} → {@code Heat.nfo}</li>
 * <li>stack {@code Heat-cd1.avi}, {@code Heat-cd2.avi} → {@code Heat.nfo}</li>
 * <li>DVD folder → {@code VIDEO_TS/VIDEO_TS.nfo}</li>
 * <li>Blu-ray folder → {@code BDMV/index.nfo}</li>
 * </ul>
 * Kodi also accepts a {@code movie.nfo} in the movie folder and, for stacks, files named after the first part
 * ({@code Heat-cd1.nfo}, as Ember writes them); both are considered when reading.
 * <p>
 * Images are called {@code <basename>-poster.jpg}, {@code -fanart.jpg} etc.; for DVD and Blu-ray folders they are
 * stored as {@code poster.jpg} etc. in the movie folder, where Kodi looks for them with disc structures.
 */
@EqualsAndHashCode
@ToString
public final class CompanionFiles {

    static final String NFO_EXTENSION = ".nfo";
    static final String FOLDER_NFO = "movie.nfo";
    /** Image formats Kodi reads as local artwork. */
    static final List<String> ARTWORK_EXTENSIONS = List.of("jpg", "jpeg", "png");
    private static final char EXTENSION_SEPARATOR = '.';
    private static final char ARTWORK_SEPARATOR = '-';

    /** Folder of the main file. */
    private final Path directory;
    /**
     * Base name as text, not as a path: Windows does not allow a space at the end of a path element, but it does
     * before the extension ({@code Film .mkv} → {@code Film .nfo}).
     */
    private final String baseName;
    /** Name of the first part without extension for stacks, otherwise {@code null}. */
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
     * @param mainFile  main file of the movie (for stacks the first part, for discs the marker file)
     * @param format    storage format
     * @param partCount number of parts; the part suffix is only removed for more than one part
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

    /** Common start of the names of video and companion files, without the part suffix for stacks. */
    public String baseName() {
        return baseName;
    }

    /** Folder of the movie; for DVD and Blu-ray folders the folder above {@code VIDEO_TS} or {@code BDMV}. */
    public Path movieDirectory() {
        return movieDirectory;
    }

    /** The NFO that KMM creates. */
    public Path nfoFile() {
        return withSuffix(NFO_EXTENSION);
    }

    /** Possible existing NFOs in the order in which they are read. */
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
     * File an image is saved as.
     *
     * @param kodiName  Kodi name of the image type, e.g. {@code poster}, {@code clearlogo}
     * @param extension file extension without dot, e.g. {@code jpg}
     */
    public Path artworkFile(String kodiName, String extension) {
        String fileName = kodiName + EXTENSION_SEPARATOR + extension;
        return disc ? movieDirectory.resolve(fileName) : withSuffix(ARTWORK_SEPARATOR + fileName);
    }

    /**
     * All files an existing image of this type may be stored as; first the names KMM saves under, then for stacks
     * the ones named after the first part.
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
