package de.tiktak.kmm.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The user interface translates the server's texts itself, under {@code server.*} in its translation files. Every
 * key of the catalog has to be there in every language, with the same English text, and no key may be left over.
 */
class UiTranslationsTest {

    private static final Path CATALOG = Path.of("src/main/resources/i18n/messages.properties");
    private static final Path UI_TRANSLATIONS = Path.of("../kmm-ui/src/i18n");
    private static final String SERVER = "server";

    @ParameterizedTest
    @ValueSource(strings = {"de", "en"})
    void containsEveryKeyOfTheCatalogAndNothingElse(String language) throws IOException {
        Map<String, String> ui = serverTexts(language);

        assertThat(ui.keySet()).containsExactlyInAnyOrderElementsOf(catalog().keySet());
    }

    @Test
    void englishTextsMatchTheCatalog() throws IOException {
        Map<String, String> english = serverTexts("en");

        assertThat(english).containsExactlyInAnyOrderEntriesOf(catalog());
    }

    private static Map<String, String> catalog() throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(CATALOG, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        Map<String, String> texts = new TreeMap<>();
        properties.stringPropertyNames().forEach(key -> texts.put(key, properties.getProperty(key)));
        return texts;
    }

    private static Map<String, String> serverTexts(String language) throws IOException {
        JsonNode root = new ObjectMapper().readTree(UI_TRANSLATIONS.resolve(language + ".json").toFile());
        Map<String, String> texts = new TreeMap<>();
        flatten(root.path(SERVER), "", texts);
        return texts;
    }

    private static void flatten(JsonNode node, String prefix, Map<String, String> texts) {
        if (node.isString()) {
            texts.put(prefix, node.asString());
            return;
        }
        node.properties().forEach(entry -> flatten(entry.getValue(),
                prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey(), texts));
    }
}
