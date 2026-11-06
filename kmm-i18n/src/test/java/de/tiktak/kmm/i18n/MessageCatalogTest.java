package de.tiktak.kmm.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Checks the real catalog in {@code src/main/resources}; the other tests of this module see the test catalog, which
 * has the same name.
 */
class MessageCatalogTest {

    private static final Path CATALOG = Path.of("src/main/resources/i18n");

    @ParameterizedTest
    @ValueSource(strings = {"messages.properties", "messages_de.properties"})
    void everyMessageIsValid(String file) throws IOException {
        Properties messages = load(file);

        messages.forEach((key, pattern) -> assertThatCode(() -> MessageTemplate.parse((String) pattern))
                .as("message %s", key).doesNotThrowAnyException());
    }

    @Test
    void everyGermanMessageHasAnEnglishOriginal() throws IOException {
        Properties english = load("messages.properties");

        assertThat(load("messages_de.properties").stringPropertyNames())
                .allSatisfy(key -> assertThat(english).containsKey(key));
    }

    private static Properties load(String file) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(CATALOG.resolve(file), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }
}
