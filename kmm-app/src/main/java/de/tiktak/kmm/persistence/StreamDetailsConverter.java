package de.tiktak.kmm.persistence;

import de.tiktak.kmm.nfo.StreamDetails;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Speichert Stream-Details als JSON in einer Spalte. Einzelne Spuren werden nie abgefragt, daher
 * lohnt keine eigene Tabelle. Leere Angaben werden als {@code NULL} gespeichert.
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
