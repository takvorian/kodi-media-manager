package de.tiktak.kmm.migration.ember.settings;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import de.tiktak.kmm.migration.ember.EmberProfile;

/**
 * Reads settings from Ember's {@code AdvancedSettings.xml} in the profile directory. It holds the settings of the
 * add-ons as {@code <Setting Section="…" Content="…" Name="…">Wert</Setting>}.
 */
public class EmberAdvancedSettingsReader {

    public static final String FILE_NAME = "AdvancedSettings.xml";

    static final String RENAME_SECTION = "generic.EmberCore.BulkRename";
    private static final String SETTING = "Setting";
    private static final String SECTION = "Section";
    private static final String CONTENT = "Content";
    private static final String NAME = "Name";

    /**
     * @return the settings of the renamer, or empty if the profile has no {@code AdvancedSettings.xml}
     * @throws EmberSettingsException if the file cannot be read
     */
    public Optional<EmberRenameSettings> readRenameSettings(EmberProfile profile) {
        Path file = profile.directory().resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        Map<String, String> values = settings(file, RENAME_SECTION);
        return Optional.of(new EmberRenameSettings(
                values.get(key("Movie", "FoldersPattern")),
                values.get(key("Movie", "FilesPattern")),
                values.get(key("TVShow", "FoldersPattern")),
                values.get(key("TVSeason", "FoldersPattern")),
                values.get(key("TVEpisode", "FilesPattern")),
                flag(values, key("Movie", "RenameEdit")),
                either(flag(values, key("Movie", "RenameSingle")), flag(values, key("Movie", "RenameMulti"))),
                flag(values, key("TVShow", "RenameEdit")),
                either(flag(values, key("TVShow", "RenameSingle")), flag(values, key("TVShow", "RenameMulti"))),
                flag(values, key("TVEpisode", "RenameUpdate"))));
    }

    private static Map<String, String> settings(Path file, String section) {
        try (InputStream input = Files.newInputStream(file)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            NodeList settings = factory.newDocumentBuilder().parse(input).getElementsByTagName(SETTING);
            Map<String, String> values = new HashMap<>();
            for (int i = 0; i < settings.getLength(); i++) {
                Element setting = (Element) settings.item(i);
                if (section.equals(setting.getAttribute(SECTION))) {
                    values.put(key(setting.getAttribute(CONTENT), setting.getAttribute(NAME)),
                            setting.getTextContent());
                }
            }
            return values;
        } catch (IOException | SAXException | ParserConfigurationException e) {
            throw new EmberSettingsException(file, e);
        }
    }

    private static String key(String content, String name) {
        return content + "/" + name;
    }

    private static Boolean flag(Map<String, String> values, String key) {
        String value = values.get(key);
        return value == null || value.isBlank() ? null : Boolean.valueOf(value.strip());
    }

    private static Boolean either(Boolean first, Boolean second) {
        if (first == null && second == null) {
            return null;
        }
        return Boolean.TRUE.equals(first) || Boolean.TRUE.equals(second);
    }
}
