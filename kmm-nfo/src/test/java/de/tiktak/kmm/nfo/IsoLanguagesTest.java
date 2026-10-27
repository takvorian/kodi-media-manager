package de.tiktak.kmm.nfo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IsoLanguagesTest {

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "deu, deu, German", "ger, deu, German", "DE, deu, German", "fre, fra, French", "eng, eng, English",
            "und, null, null", "xx, null, null", "deutsch, null, null", "null, null, null"})
    void translatesCodesToKodisThreeLetterCodesAndEnglishNames(String code, String terminology, String name) {
        assertThat(IsoLanguages.toTerminology(code)).isEqualTo(terminology);
        assertThat(IsoLanguages.englishName(code)).isEqualTo(name);
    }
}
