package de.tiktak.kmm.media;

import java.util.ArrayList;
import java.util.List;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Die Ausgabe von ffprobe für eine Eingabe, beschränkt auf die Felder aus {@link FfprobeRunner#ENTRIES}.
 *
 * @param durationSeconds Laufzeit oder {@code null}, wenn ffprobe keine kennt
 * @param bitRate         Gesamtbitrate in bit/s oder {@code null}
 */
record ProbedMedia(List<Stream> streams, Double durationSeconds, Long bitRate) {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String DOLBY_VISION = "DOVI configuration record";

    ProbedMedia {
        streams = List.copyOf(streams);
    }

    /**
     * @throws MediaProbeException wenn die Ausgabe kein JSON von ffprobe ist
     */
    static ProbedMedia parse(String input, String json) {
        JsonNode root;
        try {
            root = JSON.readTree(json);
        } catch (JacksonException e) {
            throw MediaProbeException.failed(input, "unverständliche Ausgabe");
        }
        List<Stream> streams = new ArrayList<>();
        root.path("streams").forEach(stream -> streams.add(stream(stream)));
        JsonNode format = root.path("format");
        return new ProbedMedia(streams, decimal(format.path("duration")), number(format.path("bit_rate")));
    }

    private static Stream stream(JsonNode stream) {
        JsonNode tags = stream.path("tags");
        JsonNode disposition = stream.path("disposition");
        Long bitRate = number(stream.path("bit_rate"));
        if (bitRate == null) {
            bitRate = number(tags.path("BPS"));
        }
        if (bitRate == null) {
            bitRate = number(tags.path("BPS-eng"));
        }
        boolean dolbyVision = false;
        for (JsonNode sideData : stream.path("side_data_list")) {
            dolbyVision |= DOLBY_VISION.equals(sideData.path("side_data_type").asString(null));
        }
        return new Stream(
                text(stream.path("codec_type")),
                text(stream.path("codec_name")),
                text(stream.path("codec_tag_string")),
                text(stream.path("profile")),
                integer(stream.path("width")),
                integer(stream.path("height")),
                text(stream.path("display_aspect_ratio")),
                text(stream.path("sample_aspect_ratio")),
                text(stream.path("field_order")),
                integer(stream.path("channels")),
                bitRate,
                text(stream.path("color_transfer")),
                text(tags.path("language")),
                text(tags.path("title")),
                text(tags.path("stereo_mode")),
                disposition.path("forced").asInt(0) == 1,
                disposition.path("attached_pic").asInt(0) == 1,
                dolbyVision);
    }

    private static String text(JsonNode node) {
        String value = node.isMissingNode() || node.isNull() ? null : node.asString().strip();
        return value == null || value.isEmpty() ? null : value;
    }

    private static Integer integer(JsonNode node) {
        Long value = number(node);
        return value == null ? null : Math.toIntExact(value);
    }

    /** ffprobe liefert Zahlen teils als Text, unbekannte Werte als {@code N/A}. */
    private static Long number(JsonNode node) {
        Double value = decimal(node);
        return value == null ? null : Math.round(value);
    }

    private static Double decimal(JsonNode node) {
        String text = text(node);
        if (text == null) {
            return null;
        }
        try {
            double value = Double.parseDouble(text);
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Eine Spur, wie ffprobe sie beschreibt. */
    record Stream(String type, String codec, String codecTag, String profile, Integer width, Integer height,
            String displayAspectRatio, String sampleAspectRatio, String fieldOrder, Integer channels, Long bitRate,
            String colorTransfer, String language, String title, String stereoMode, boolean forced,
            boolean attachedPicture, boolean dolbyVision) {

        static final String VIDEO = "video";
        static final String AUDIO = "audio";
        static final String SUBTITLE = "subtitle";

        boolean is(String streamType) {
            return streamType.equals(type);
        }
    }
}
