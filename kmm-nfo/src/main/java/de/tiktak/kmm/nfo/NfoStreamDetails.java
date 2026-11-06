package de.tiktak.kmm.nfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Reads and writes {@code <fileinfo><streamdetails>} in Ember's format: streams and elements in alphabetical order,
 * plus {@code longlanguage}, {@code filesize} and the type of the subtitles. Kodi reads only what it knows.
 */
final class NfoStreamDetails {

    static final String FILE_INFO = "fileinfo";
    private static final String STREAM_DETAILS = "streamdetails";
    private static final String VIDEO = "video";
    private static final String AUDIO = "audio";
    private static final String SUBTITLE = "subtitle";
    private static final String CODEC = "codec";
    private static final String ASPECT = "aspect";
    private static final String WIDTH = "width";
    private static final String HEIGHT = "height";
    private static final String DURATION = "durationinseconds";
    private static final String BITRATE = "bitrate";
    private static final String SCAN_TYPE = "scantype";
    private static final String STEREO_MODE = "stereomode";
    private static final String HDR_TYPE = "hdrtype";
    private static final String MULTI_VIEW_COUNT = "multiview_count";
    private static final String MULTI_VIEW_LAYOUT = "multiview_layout";
    private static final String FILE_SIZE = "filesize";
    private static final String LANGUAGE = "language";
    private static final String LONG_LANGUAGE = "longlanguage";
    private static final String CHANNELS = "channels";
    private static final String FORCED = "forced";
    private static final String PATH = "path";
    private static final String TYPE = "type";
    private static final String EMBEDDED = "Embedded";
    private static final String EXTERNAL = "External";

    private NfoStreamDetails() {
    }

    static StreamDetails read(Element root) {
        List<Element> fileInfos = NfoDocuments.children(root, FILE_INFO);
        if (fileInfos.isEmpty()) {
            return StreamDetails.EMPTY;
        }
        List<Element> details = NfoDocuments.children(fileInfos.getFirst(), STREAM_DETAILS);
        if (details.isEmpty()) {
            return StreamDetails.EMPTY;
        }
        Element streams = details.getFirst();
        return new StreamDetails(
                NfoDocuments.children(streams, VIDEO).stream().map(NfoStreamDetails::video).toList(),
                NfoDocuments.children(streams, AUDIO).stream().map(NfoStreamDetails::audio).toList(),
                NfoDocuments.children(streams, SUBTITLE).stream().map(NfoStreamDetails::subtitle).toList());
    }

    /** Replaces {@code <fileinfo>}; without known streams the NFO stays unchanged. */
    static void write(Document document, Element root, StreamDetails details) {
        if (details == null || details.isEmpty()) {
            return;
        }
        Element fileInfo = document.createElement(FILE_INFO);
        Element streams = document.createElement(STREAM_DETAILS);
        fileInfo.appendChild(streams);
        for (Stream stream : streams(details)) {
            Element element = document.createElement(stream.name());
            stream.fields().forEach((name, value) -> element.appendChild(NfoDomEditor.textElement(document, name,
                    value)));
            streams.appendChild(element);
        }
        NfoDomEditor.replaceAll(root, FILE_INFO, List.of(fileInfo));
    }

    /**
     * The {@code <fileinfo>} block as text, indented like the rest of the file.
     *
     * @param indent  indentation of a child element of the root element, at the same time one indentation level
     * @param newline line ending of the file
     */
    static String toText(StreamDetails details, String indent, String newline) {
        StringBuilder text = new StringBuilder();
        text.append('<').append(FILE_INFO).append('>').append(newline);
        text.append(indent).append(indent).append('<').append(STREAM_DETAILS).append('>').append(newline);
        String streamIndent = indent.repeat(3);
        for (Stream stream : streams(details)) {
            text.append(streamIndent).append('<').append(stream.name()).append('>').append(newline);
            stream.fields().forEach((name, value) -> text.append(streamIndent).append(indent)
                    .append(NfoTextEditor.tag(name, value)).append(newline));
            text.append(streamIndent).append("</").append(stream.name()).append('>').append(newline);
        }
        text.append(indent).append(indent).append("</").append(STREAM_DETAILS).append('>').append(newline);
        return text.append(indent).append("</").append(FILE_INFO).append('>').toString();
    }

    private static List<Stream> streams(StreamDetails details) {
        List<Stream> streams = new ArrayList<>();
        details.audios().forEach(audio -> streams.add(new Stream(AUDIO, fields(audio))));
        details.subtitles().forEach(subtitle -> streams.add(new Stream(SUBTITLE, fields(subtitle))));
        details.videos().forEach(video -> streams.add(new Stream(VIDEO, fields(video))));
        return streams;
    }

    private static Map<String, String> fields(StreamDetails.Audio audio) {
        Fields fields = new Fields();
        fields.put(BITRATE, audio.bitrate());
        fields.put(CHANNELS, audio.channels());
        fields.put(CODEC, audio.codec());
        fields.putLanguage(audio.language());
        return fields.values;
    }

    private static Map<String, String> fields(StreamDetails.Subtitle subtitle) {
        Fields fields = new Fields();
        fields.putLanguage(subtitle.language());
        fields.put(FORCED, subtitle.forced());
        fields.put(PATH, subtitle.path());
        fields.put(TYPE, subtitle.isExternal() ? EXTERNAL : EMBEDDED);
        return fields.values;
    }

    private static Map<String, String> fields(StreamDetails.Video video) {
        Fields fields = new Fields();
        fields.put(ASPECT, video.aspect() == null ? null : String.format(Locale.ROOT, "%.3f", video.aspect()));
        fields.put(BITRATE, video.bitrate());
        fields.put(CODEC, video.codec());
        fields.put(DURATION, video.durationSeconds());
        fields.put(HDR_TYPE, video.hdrType());
        fields.put(HEIGHT, video.height());
        fields.putLanguage(video.language());
        fields.put(MULTI_VIEW_COUNT, video.multiViewCount());
        fields.put(MULTI_VIEW_LAYOUT, video.multiViewLayout());
        fields.put(SCAN_TYPE, video.scanType());
        fields.put(STEREO_MODE, video.stereoMode());
        fields.put(WIDTH, video.width());
        fields.put(FILE_SIZE, video.fileSize());
        return fields.values;
    }

    private static StreamDetails.Video video(Element video) {
        return new StreamDetails.Video(
                NfoDocuments.text(video, CODEC),
                decimal(NfoDocuments.text(video, ASPECT)),
                NfoSupplementReader.integer(NfoDocuments.text(video, WIDTH)),
                NfoSupplementReader.integer(NfoDocuments.text(video, HEIGHT)),
                NfoSupplementReader.integer(NfoDocuments.text(video, DURATION)),
                NfoSupplementReader.integer(NfoDocuments.text(video, BITRATE)),
                NfoDocuments.text(video, SCAN_TYPE),
                NfoDocuments.text(video, STEREO_MODE),
                NfoDocuments.text(video, LANGUAGE),
                NfoDocuments.text(video, HDR_TYPE),
                NfoDocuments.text(video, MULTI_VIEW_COUNT),
                NfoDocuments.text(video, MULTI_VIEW_LAYOUT),
                fileSize(NfoDocuments.text(video, FILE_SIZE)));
    }

    private static StreamDetails.Audio audio(Element audio) {
        return new StreamDetails.Audio(
                NfoDocuments.text(audio, CODEC),
                NfoDocuments.text(audio, LANGUAGE),
                NfoSupplementReader.integer(NfoDocuments.text(audio, CHANNELS)),
                NfoSupplementReader.integer(NfoDocuments.text(audio, BITRATE)));
    }

    /** Ember marks external subtitles with the type {@code External} and their path. */
    private static StreamDetails.Subtitle subtitle(Element subtitle) {
        String path = NfoDocuments.text(subtitle, PATH);
        boolean external = EXTERNAL.equalsIgnoreCase(NfoDocuments.text(subtitle, TYPE)) || path != null;
        return new StreamDetails.Subtitle(NfoDocuments.text(subtitle, LANGUAGE),
                Boolean.parseBoolean(NfoDocuments.text(subtitle, FORCED)), external ? nonNull(path) : null);
    }

    private static String nonNull(String path) {
        return path == null ? "" : path;
    }

    private static Double decimal(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(value.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Ember writes the size as a floating-point number, Kodi as an integer. */
    private static Long fileSize(String value) {
        Double size = decimal(value);
        return size == null || size <= 0 ? null : Math.round(size);
    }

    private record Stream(String name, Map<String, String> fields) {
    }

    /** Elements of a stream in writing order; empty values are dropped. */
    private static final class Fields {

        private final Map<String, String> values = new LinkedHashMap<>();

        void put(String name, Object value) {
            if (value != null && !value.toString().isBlank()) {
                values.put(name, value.toString());
            }
        }

        void putLanguage(String language) {
            put(LANGUAGE, language);
            if (language != null) {
                put(LONG_LANGUAGE, IsoLanguages.englishName(language));
            }
        }
    }
}
