package de.tiktak.kmm.persistence;

import de.tiktak.kmm.nfo.StreamDetails;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stores stream details as JSON in a single column. Individual tracks are never queried, so
 * a separate table is not worthwhile. Empty details are stored as {@code NULL}.
 */
@Converter
public class StreamDetailsConverter implements AttributeConverter<StreamDetails, String> {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Override
    public String convertToDatabaseColumn(StreamDetails details) {
        return details == null || details.isEmpty() ? null : JSON.writeValueAsString(details);
    }

    @Override
    public StreamDetails convertToEntityAttribute(String json) {
        return json == null || json.isBlank() ? StreamDetails.EMPTY : JSON.readValue(json, StreamDetails.class);
    }
}
