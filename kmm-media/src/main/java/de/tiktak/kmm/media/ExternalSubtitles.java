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
 * Findet Untertiteldateien neben einer Videodatei, etwa {@code Heat.de.forced.srt} zu {@code Heat.mkv},
 * und liest Sprache und „forced“ aus dem Dateinamen.
 */
@Slf4j
final class ExternalSubtitles {

    private static final Set<String> EXTENSIONS = Set.of("srt", "ass", "ssa", "sub", "idx", "sup", "vtt", "smi");
    /** VobSub besteht aus {@code .idx} und {@code .sub}; gezählt wird nur die {@code .idx}. */
    private static final String VOBSUB_INDEX = "idx";
    private static final String VOBSUB_DATA = "sub";
    private static final String FORCED = "forced";
    private static final char SEPARATOR = '.';

    private ExternalSubtitles() {
    }

    /** @return die Untertitel in der Reihenfolge ihrer Dateinamen; leer, wenn der Ordner nicht lesbar ist */
    static List<StreamDetails.Subtitle> of(Path videoFile) {
        String fileName = videoFile.getFileName().toString();
        int extensionStart = fileName.lastIndexOf(SEPARATOR);
        String prefix = (extensionStart > 0 ? fileName.substring(0, extensionStart) : fileName) + SEPARATOR;
        try (Stream<Path> siblings = Files.list(videoFile.toAbsolutePath().getParent())) {
            List<Path> files = siblings.filter(file -> file.getFileName().toString().startsWith(prefix))
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
        for (String token : middle.split("[._ -]+")) {
            forced |= FORCED.equalsIgnoreCase(token);
            if (language == null) {
                language = IsoLanguages.toTerminology(token);
            }
        }
        return new StreamDetails.Subtitle(language, forced, file.toAbsolutePath().toString());
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
