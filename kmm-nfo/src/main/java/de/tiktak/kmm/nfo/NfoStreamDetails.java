package de.tiktak.kmm.nfo;

import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Liest und schreibt {@code <fileinfo><streamdetails>}. Ember schreibt zusätzlich Elemente wie
 * {@code longlanguage} oder {@code filesize}; sie werden ignoriert.
 */
final class NfoStreamDetails {

    private static final String FILE_INFO = "fileinfo";
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
    private static final String LANGUAGE = "language";
    private static final String CHANNELS = "channels";
    private static final String FORCED = "forced";

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

    /** Ersetzt {@code <fileinfo>}; ohne bekannte Spuren bleibt die NFO unverändert. */
    static void write(Document document, Element root, StreamDetails details) {
        if (details == null || details.isEmpty()) {
            return;
        }
        Element fileInfo = document.createElement(FILE_INFO);
        Element streams = document.createElement(STREAM_DETAILS);
        fileInfo.appendChild(streams);
        details.videos().forEach(video -> streams.appendChild(video(document, video)));
        details.audios().forEach(audio -> streams.appendChild(audio(document, audio)));
        details.subtitles().forEach(subtitle -> streams.appendChild(subtitle(document, subtitle)));
        NfoDomEditor.replaceAll(root, FILE_INFO, List.of(fileInfo));
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
                NfoDocuments.text(video, LANGUAGE));
    }

    private static StreamDetails.Audio audio(Element audio) {
        return new StreamDetails.Audio(
                NfoDocuments.text(audio, CODEC),
                NfoDocuments.text(audio, LANGUAGE),
                NfoSupplementReader.integer(NfoDocuments.text(audio, CHANNELS)),
                NfoSupplementReader.integer(NfoDocuments.text(audio, BITRATE)));
    }

    private static StreamDetails.Subtitle subtitle(Element subtitle) {
        return new StreamDetails.Subtitle(NfoDocuments.text(subtitle, LANGUAGE),
                Boolean.parseBoolean(NfoDocuments.text(subtitle, FORCED)));
    }

    private static Element video(Document document, StreamDetails.Video video) {
        Element element = document.createElement(VIDEO);
        append(document, element, CODEC, video.codec());
        append(document, element, ASPECT, video.aspect());
        append(document, element, WIDTH, video.width());
        append(document, element, HEIGHT, video.height());
        append(document, element, DURATION, video.durationSeconds());
        append(document, element, BITRATE, video.bitrate());
        append(document, element, SCAN_TYPE, video.scanType());
        append(document, element, STEREO_MODE, video.stereoMode());
        append(document, element, LANGUAGE, video.language());
        return element;
    }

    private static Element audio(Document document, StreamDetails.Audio audio) {
        Element element = document.createElement(AUDIO);
        append(document, element, CODEC, audio.codec());
        append(document, element, LANGUAGE, audio.language());
        append(document, element, CHANNELS, audio.channels());
        append(document, element, BITRATE, audio.bitrate());
        return element;
    }

    private static Element subtitle(Document document, StreamDetails.Subtitle subtitle) {
        Element element = document.createElement(SUBTITLE);
        append(document, element, LANGUAGE, subtitle.language());
        if (subtitle.forced()) {
            append(document, element, FORCED, Boolean.TRUE);
        }
        return element;
    }

    private static void append(Document document, Element parent, String name, Object value) {
        if (value != null && !value.toString().isBlank()) {
            parent.appendChild(NfoDomEditor.textElement(document, name, value.toString()));
        }
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
}
