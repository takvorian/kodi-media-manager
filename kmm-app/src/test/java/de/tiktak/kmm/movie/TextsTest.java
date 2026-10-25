package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextsTest {

    @Test
    void trimsAndTreatsBlankAsMissing() {
        assertThat(Texts.clean("  Heat ", 10)).isEqualTo("Heat");
        assertThat(Texts.clean("   ", 10)).isNull();
        assertThat(Texts.clean(null, 10)).isNull();
    }

    @Test
    void truncatesWithoutTrailingWhitespace() {
        assertThat(Texts.clean("Heat Ronin", 5)).isEqualTo("Heat");
    }
}
