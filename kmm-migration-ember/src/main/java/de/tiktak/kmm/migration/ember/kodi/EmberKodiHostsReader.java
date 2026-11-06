package de.tiktak.kmm.migration.ember.kodi;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import de.tiktak.kmm.migration.ember.EmberProfile;
import de.tiktak.kmm.migration.ember.EmberProfileException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.dataformat.xml.XmlMapper;

/**
 * Reads the Kodi devices from Ember's {@code Interface.Kodi.xml} in the profile directory.
 */
public class EmberKodiHostsReader {

    public static final String FILE_NAME = "Interface.Kodi.xml";

    private final XmlMapper xmlMapper = XmlMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /**
     * @return the devices, or an empty list if the profile contains no Kodi settings
     * @throws EmberProfileException if the file cannot be read
     */
    public List<EmberKodiHost> read(EmberProfile profile) {
        Path file = profile.directory().resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try (InputStream input = Files.newInputStream(file)) {
            return xmlMapper.readValue(input, EmberKodiXml.class).toHosts();
        } catch (IOException | JacksonException e) {
            throw EmberProfileException.unreadableKodiSettings(file, e);
        }
    }
}
