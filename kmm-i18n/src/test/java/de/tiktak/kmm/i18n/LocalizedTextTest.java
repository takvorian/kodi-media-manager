package de.tiktak.kmm.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;

class LocalizedTextTest {

    @Test
    void collectsArgumentsInOrderWithoutChangingTheOriginal() {
        LocalizedText base = LocalizedText.of("key");
        LocalizedText text = base.with("b", 2).with("a", "x");

        assertThat(base.args()).isEmpty();
        assertThat(text.args()).containsExactly(Map.entry("b", 2), Map.entry("a", "x"));
    }

    @Test
    void keepsSimpleValuesAndStoresOthersAsText() {
        LocalizedText nested = LocalizedText.of("nested");
        LocalizedText text = LocalizedText.of("key")
                .with("number", 3L).with("flag", true).with("nested", nested)
                .with("path", Path.of("movies")).with("type", Thread.State.NEW).with("missing", null);

        assertThat(text.args()).containsEntry("number", 3L).containsEntry("flag", true)
                .containsEntry("nested", nested).containsEntry("path", "movies").containsEntry("type", "NEW")
                .containsEntry("missing", null);
    }

    @Test
    void argumentsCannotBeChangedFromOutside() {
        LocalizedText text = LocalizedText.of("key").with("a", 1);

        assertThatThrownBy(() -> text.args().put("b", 2)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void marksLiterals() {
        LocalizedText literal = LocalizedText.literal("movie.mkv");

        assertThat(literal.showsAsIs()).isTrue();
        assertThat(literal.args()).containsEntry(LocalizedText.LITERAL_ARGUMENT, "movie.mkv");
        assertThat(LocalizedText.of("key").showsAsIs()).isFalse();
    }

    @Test
    void requiresKeyAndArgumentName() {
        assertThatNullPointerException().isThrownBy(() -> LocalizedText.of(null));
        assertThatNullPointerException().isThrownBy(() -> LocalizedText.of("key").with(null, 1));
    }

    @Test
    void exceptionsCarryTheTextAndAnEnglishMessage() {
        LocalizedText text = LocalizedText.of("test.greeting").with("name", "Kodi");
        IllegalStateException cause = new IllegalStateException();

        LocalizedException exception = new LocalizedException(text, cause);

        assertThat(exception.localizedText()).isEqualTo(text);
        assertThat(exception.getMessage()).isEqualTo("Hello Kodi");
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(new LocalizedException(text).getCause()).isNull();
    }
}
