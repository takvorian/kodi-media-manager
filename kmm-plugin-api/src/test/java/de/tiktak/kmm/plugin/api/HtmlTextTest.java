package de.tiktak.kmm.plugin.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HtmlTextTest {

    @Test
    void removesTagsAndDecodesEntities() {
        assertThat(HtmlText.plain("<p>Tom &amp; <a href=\"/x\">Jerry</a>&nbsp;&#8211; &#x263A; &auml; &unbekannt;</p>"))
                .isEqualTo("Tom & Jerry – ☺ ä &unbekannt;");
    }

    @Test
    void turnsParagraphsIntoLinesWithoutBlankOnes() {
        assertThat(HtmlText.plain("<p>Eins</p>\n\n<p>Zwei<br />Drei</p>")).isEqualTo("Eins\nZwei\nDrei");
    }

    @Test
    void keepsImpossibleEntitiesAndHandlesNothing() {
        assertThat(HtmlText.plain("&#99999999999; &#x110000;")).isEqualTo("&#99999999999; &#x110000;");
        assertThat(HtmlText.plain(null)).isEmpty();
    }
}
