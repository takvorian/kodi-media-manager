package de.tiktak.kmm.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import de.tiktak.kmm.nfo.IsoLanguages;
import de.tiktak.kmm.nfo.StreamDetails;
import lombok.extern.slf4j.Slf4j;

/**
 * Finds subtitle files next to a video file, such as {@code Heat.de.forced.srt} or {@code Heat-eng.idx} for
 * {@code Heat.mkv}, and reads the language and "forced" from the file name.
 */
@Slf4j
final class ExternalSubtitles {

    private static final Set<String> EXTENSIONS = Set.of("srt", "ass", "ssa", "sub", "idx", "sup", "vtt", "smi");
    /** VobSub consists of {@code .idx} and {@code .sub}; only the {@code .idx} is counted. */
    private static final String VOBSUB_INDEX = "idx";
    private static final String VOBSUB_DATA = "sub";
    private static final String FORCED = "forced";
    private static final char SEPARATOR = '.';
    /** Characters that separate the video name from the rest of the subtitle file name, as in Kodi. */
    private static final String NAME_SEPARATORS = "._ -";
    private static final String TOKEN_SEPARATORS = "[._ -]+";

    private ExternalSubtitles() {
    }

    /** @return the subtitles in the order of their file names; empty if the folder cannot be read */
    static List<StreamDetails.Subtitle> of(Path videoFile) {
        String fileName = videoFile.getFileName().toString();
        int extensionStart = fileName.lastIndexOf(SEPARATOR);
        String prefix = extensionStart > 0 ? fileName.substring(0, extensionStart) : fileName;
        try (Stream<Path> siblings = Files.list(videoFile.toAbsolutePath().getParent())) {
            List<Path> files = siblings.filter(file -> belongsTo(file.getFileName().toString(), prefix))
                    .filter(file -> EXTENSIONS.contains(extension(file)))
                    .sorted()
                    .toList();
            List<StreamDetails.Subtitle> subtitles = new ArrayList<>();
            for (Path file : files) {
                if (!isVobSubData(file, files)) {
                    subtitles.add(subtitle(file, prefix));
                }
            }
            return subtitles;
        } catch (IOException e) {
            log.warn("Subtitles next to {} cannot be listed: {}", videoFile, e.getMessage());
            return List.of();
        }
    }

    private static StreamDetails.Subtitle subtitle(Path file, String prefix) {
        String name = file.getFileName().toString();
        String middle = name.substring(prefix.length(), name.length() - extension(file).length());
        String language = null;
        boolean forced = false;
        for (String token : middle.split(TOKEN_SEPARATORS)) {
            forced |= FORCED.equalsIgnoreCase(token);
            if (language == null) {
                language = IsoLanguages.toTerminology(token);
            }
        }
        return new StreamDetails.Subtitle(language, forced, file.toAbsolutePath().toString());
    }

    private static boolean belongsTo(String name, String prefix) {
        return name.length() > prefix.length() && name.startsWith(prefix)
                && NAME_SEPARATORS.indexOf(name.charAt(prefix.length())) >= 0;
    }

    private static boolean isVobSubData(Path file, List<Path> files) {
        if (!VOBSUB_DATA.equals(extension(file))) {
            return false;
        }
        String name = file.getFileName().toString();
        String index = name.substring(0, name.length() - VOBSUB_DATA.length()) + VOBSUB_INDEX;
        return files.stream().anyMatch(other -> other.getFileName().toString().equalsIgnoreCase(index));
    }

    private static String extension(Path file) {
        String name = file.getFileName().toString();
        int extensionStart = name.lastIndexOf(SEPARATOR);
        return extensionStart < 0 ? "" : name.substring(extensionStart + 1).toLowerCase(Locale.ROOT);
    }
}
