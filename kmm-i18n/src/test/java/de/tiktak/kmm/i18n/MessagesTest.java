package de.tiktak.kmm.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.Test;

/** Uses the test catalog in {@code src/test/resources/i18n}. */
class MessagesTest {

    @Test
    void rendersInTheRequestedLanguage() {
        LocalizedText greeting = LocalizedText.of("test.greeting").with("name", "Kodi");

        assertThat(Messages.render(greeting, Locale.GERMAN)).isEqualTo("Hallo Kodi");
        assertThat(Messages.render(greeting, Locale.ENGLISH)).isEqualTo("Hello Kodi");
        assertThat(Messages.english(greeting)).isEqualTo("Hello Kodi");
    }

    @Test
    void mapsRegionalAndUnsupportedLocales() {
        LocalizedText movies = LocalizedText.of("test.movies").with("count", 2);

        assertThat(Messages.render(movies, Locale.forLanguageTag("de-AT"))).isEqualTo("2 Filme");
        assertThat(Messages.render(movies, Locale.UK)).isEqualTo("2 movies");
        assertThat(Messages.render(movies, Locale.FRENCH)).isEqualTo("2 movies");
        assertThat(Messages.render(movies, null)).isEqualTo("2 movies");
    }

    @Test
    void ignoresTheDefaultLocaleOfTheJvm() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);

            assertThat(Messages.english(LocalizedText.of("test.greeting").with("name", "x"))).isEqualTo("Hello x");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void fallsBackToEnglishAndThenToTheKey() {
        assertThat(Messages.render(LocalizedText.of("test.englishOnly"), Locale.GERMAN)).isEqualTo("Only in English");
        assertThat(Messages.render(LocalizedText.of("test.unknown"), Locale.GERMAN)).isEqualTo("test.unknown");
    }

    @Test
    void rendersNestedTextsInTheSameLanguage() {
        LocalizedText running = LocalizedText.of("test.running")
                .with("job", LocalizedText.of("test.movies").with("count", 1));

        assertThat(Messages.render(running, Locale.GERMAN)).isEqualTo("1 Film läuft bereits.");
    }

    @Test
    void showsLiteralsAsTheyAre() {
        assertThat(Messages.render(LocalizedText.literal("Film {1}.mkv"), Locale.GERMAN)).isEqualTo("Film {1}.mkv");
        assertThat(Messages.english(LocalizedText.literal(null))).isEmpty();
    }

    @Test
    void knowsWhichKeysExist() {
        assertThat(Messages.contains("test.greeting")).isTrue();
        assertThat(Messages.contains("test.unknown")).isFalse();
    }
}
