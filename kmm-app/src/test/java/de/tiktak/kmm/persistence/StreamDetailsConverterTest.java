package de.tiktak.kmm.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tiktak.kmm.nfo.StreamDetails;

class StreamDetailsConverterTest {

    private final StreamDetailsConverter converter = new StreamDetailsConverter();

    @Test
    void storesStreamDetailsAsJsonAndReadsThemBack() {
        StreamDetails details = new StreamDetails(
                List.of(new StreamDetails.Video("h264", 1.78, 1920, 1080, 5400, null, "Progressive", null, null)),
                List.of(new StreamDetails.Audio("dca", "deu", 6, 1509)),
                List.of(new StreamDetails.Subtitle("eng", true)));

        String json = converter.convertToDatabaseColumn(details);

        assertThat(json).contains("\"codec\":\"h264\"", "\"forced\":true");
        assertThat(converter.convertToEntityAttribute(json)).isEqualTo(details);
    }

    @Test
    void storesEmptyDetailsAsNull() {
        assertThat(converter.convertToDatabaseColumn(StreamDetails.EMPTY)).isNull();
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isEqualTo(StreamDetails.EMPTY);
        assertThat(converter.convertToEntityAttribute(" ")).isEqualTo(StreamDetails.EMPTY);
    }
}
