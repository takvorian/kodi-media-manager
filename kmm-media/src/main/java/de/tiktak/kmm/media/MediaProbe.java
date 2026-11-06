package de.tiktak.kmm.media;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import de.tiktak.kmm.nfo.StreamDetails;
import de.tiktak.kmm.scanner.DiscFormat;
import de.tiktak.kmm.scanner.StackedFiles;

/**
 * Reads the technical details of a movie or an episode with ffprobe. Multi-part movies count with the runtime and
 * size of all parts; for DVDs the longest title is examined, for Blu-rays the largest video file.
 */
public class MediaProbe {

    /** Files of a DVD title: {@code VTS_01_1.VOB}; part 0 is the menu. */
    private static final Pattern DVD_TITLE_PART = Pattern.compile("(?i)VTS_(\\d{2})_([1-9]\\d*)\\.VOB");
    private static final String BLURAY_STREAMS = "STREAM";
    private static final String BLURAY_VIDEO_EXTENSION = ".m2ts";
    private static final String CONCAT = "concat:";
    private static final String CONCAT_SEPARATOR = "|";

    private final ProbeRunner runner;

    public MediaProbe(ProbeRunner runner) {
        this.runner = Objects.requireNonNull(runner, "runner must not be null");
    }

    /**
     * @param mainFile the video file; for multi-part movies the first part, for DVDs the {@code VIDEO_TS.IFO} and
     *                 for Blu-rays the {@code index.bdmv}
     * @throws MediaProbeException if the file cannot be read or contains no streams
     */
    public StreamDetails probe(Path mainFile, DiscFormat format) {
        Objects.requireNonNull(mainFile, "mainFile must not be null");
        return switch (Objects.requireNonNull(format, "format must not be null")) {
            case FILE -> probeFiles(mainFile);
            case DVD -> probeInput(dvdTitle(mainFile));
            case BLURAY -> probeInput(List.of(largestBlurayStream(mainFile)));
        };
    }

    private StreamDetails probeFiles(Path mainFile) {
        List<Path> parts = parts(mainFile);
        ProbedMedia first = run(parts.getFirst().toString());
        double duration = valueOrZero(first.durationSeconds());
        for (Path part : parts.subList(1, parts.size())) {
            duration += valueOrZero(run(part.toString()).durationSeconds());
        }
        StreamDetails streams = KodiStreams.toStreamDetails(first, duration > 0 ? duration : null, size(parts));
        List<StreamDetails.Subtitle> subtitles = new ArrayList<>(streams.subtitles());
        subtitles.addAll(ExternalSubtitles.of(parts.getFirst()));
        return new StreamDetails(streams.videos(), streams.audios(), subtitles);
    }

    /** ffprobe reads several files of a title in one go so the runtime covers the whole title. */
    private StreamDetails probeInput(List<Path> files) {
        String input = files.size() == 1 ? files.getFirst().toString()
                : CONCAT + files.stream().map(Path::toString).collect(Collectors.joining(CONCAT_SEPARATOR));
        ProbedMedia media = run(input);
        return KodiStreams.toStreamDetails(media, media.durationSeconds(), size(files));
    }

    private ProbedMedia run(String input) {
        ProbedMedia media = ProbedMedia.parse(input, runner.run(input));
        if (media.streams().isEmpty()) {
            throw MediaProbeException.noStreams(input);
        }
        return media;
    }

    private static List<Path> parts(Path mainFile) {
        try {
            return StackedFiles.parts(mainFile);
        } catch (UncheckedIOException e) {
            throw MediaProbeException.unreadable(mainFile.getParent(), e);
        }
    }

    /** @return the parts of the title with the largest amount of data, in their order */
    private static List<Path> dvdTitle(Path ifoFile) {
        Path directory = ifoFile.toAbsolutePath().getParent();
        Map<String, List<Path>> titles = new TreeMap<>();
        for (Path file : list(directory)) {
            Matcher part = DVD_TITLE_PART.matcher(file.getFileName().toString());
            if (part.matches()) {
                titles.computeIfAbsent(part.group(1), title -> new ArrayList<>()).add(file);
            }
        }
        return titles.values().stream()
                .max(Comparator.comparingLong(MediaProbe::size))
                .map(parts -> parts.stream().sorted(Comparator.comparing(MediaProbe::dvdPartNumber)).toList())
                .orElseThrow(() -> MediaProbeException.noStreams(directory.toString()));
    }

    private static Path largestBlurayStream(Path indexFile) {
        Path directory = indexFile.toAbsolutePath().getParent();
        Optional<Path> streams = list(directory).stream()
                .filter(file -> file.getFileName().toString().equalsIgnoreCase(BLURAY_STREAMS))
                .findFirst();
        return streams.stream()
                .flatMap(streamDirectory -> list(streamDirectory).stream())
                .filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(BLURAY_VIDEO_EXTENSION))
                .max(Comparator.comparingLong(file -> size(List.of(file))))
                .orElseThrow(() -> MediaProbeException.noStreams(directory.toString()));
    }

    private static int dvdPartNumber(Path file) {
        Matcher part = DVD_TITLE_PART.matcher(file.getFileName().toString());
        return part.matches() ? Integer.parseInt(part.group(2)) : 0;
    }

    private static List<Path> list(Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            return files.toList();
        } catch (IOException e) {
            throw MediaProbeException.unreadable(directory, e);
        }
    }

    private static long size(List<Path> files) {
        long size = 0;
        for (Path file : files) {
            try {
                size += Files.size(file);
            } catch (IOException e) {
                throw MediaProbeException.unreadable(file, e);
            }
        }
        return size;
    }

    private static double valueOrZero(Double value) {
        return value == null ? 0 : value;
    }
}
