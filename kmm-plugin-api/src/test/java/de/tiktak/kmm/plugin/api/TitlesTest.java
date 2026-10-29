package de.tiktak.kmm.plugin.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TitlesTest {

    @Test
    void normalizesTitlesForComparison() {
        assertThat(Titles.normalize("The Muppet Show")).isEqualTo("muppetshow");
        assertThat(Titles.normalize("Die Märchenbraut")).isEqualTo("marchenbraut");
        assertThat(Titles.normalize(" 11.22.63 ")).isEqualTo("112263");
        assertThat(Titles.normalize(null)).isEmpty();
    }

    @Test
    void comparesTitlesButNeverEmptyOnes() {
        assertThat(Titles.same("ALF", "Alf")).isTrue();
        assertThat(Titles.same("Cooper's Half Hour", "Coopers Half-Hour")).isTrue();
        assertThat(Titles.same("ALF", "ALF – Die Serie")).isFalse();
        assertThat(Titles.same("...", "!!!")).isFalse();
        assertThat(Titles.same(null, null)).isFalse();
    }
}
