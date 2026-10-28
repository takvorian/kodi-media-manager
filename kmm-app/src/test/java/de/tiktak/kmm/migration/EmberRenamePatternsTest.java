package de.tiktak.kmm.migration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EmberRenamePatternsTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "$T {($Y)}|{title} [({year})]",
        "$T{.$S}|{title}[.{source}]",
        "$T{ ($6)}{ ($Y)}|{title}[ ({edition})][ ({year})]",
        "Staffel $K1_?|Staffel {season}",
        "Season $K2_?|Season {season:2}",
        "$Z|{show}",
        "$Z - $W2_S?2E?{ - $T}|{show} - S{season:2}E{episode:2}[ - {title}]",
        "$Z $Q3E?|{show} E{episode:3}",
        "$1\\$T ($Y)|{sorttitle:first}\\{title} ({year})",
        "$OO $O $L $N $5 $I $R $H $J $A $M $P $G $C $U $V $2 $E|"
                + "{originaltitle} {originaltitle} {title:sort} {set} {set:sort} {imdb} {resolution} {videocodec} "
                + "{audiocodec} {channels} {mpaa} {rating} {genres} {directors} {countries} {3d} {aired} {sorttitle}",
        "$T [Extended]|{title} [[Extended]]"
    })
    void translatesEmberPatterns(String ember, String expected) {
        assertThat(EmberRenamePatterns.translate(ember)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"$D", "$F", "$T$X.", "$?a?b?$T", "$;$T", "$B$T", "$-Y"})
    void rejectsPartsWithoutCounterpart(String ember) {
        assertThat(EmberRenamePatterns.translate(ember)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "  ")
    void ignoresMissingPatterns(String ember) {
        assertThat(EmberRenamePatterns.translate(ember)).isEmpty();
    }
}
