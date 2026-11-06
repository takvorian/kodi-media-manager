package de.tiktak.kmm.migration.ember.settings;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.dataformat.xml.XmlMapper;

/**
 * Reads Ember's {@code Settings.xml}.
 */
public class EmberSettingsReader {

    private final XmlMapper xmlMapper = XmlMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public EmberSettings read(Path settingsFile) {
        try (InputStream input = Files.newInputStream(settingsFile)) {
            return xmlMapper.readValue(input, EmberSettingsXml.class).toSettings();
        } catch (IOException | JacksonException e) {
            throw new EmberSettingsException(settingsFile, e);
        }
    }
}
