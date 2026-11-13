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

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "gsw, null, gsw, Swiss German", "NDS, null, nds, Low German", "ger, deu, deu, German",
            "sdh, null, sdh, Southern Kurdish", "mul, null, null, null", "zxx, null, null, null",
            "qaa, null, null, null", "dts, null, null, null"})
    void acceptsLanguagesWithOnlyAThreeLetterCodeInStreamsButNotInFileNames(String code, String terminology,
            String streamLanguage, String name) {
        assertThat(IsoLanguages.toTerminology(code)).isEqualTo(terminology);
        assertThat(IsoLanguages.streamLanguage(code)).isEqualTo(streamLanguage);
        assertThat(IsoLanguages.englishName(code)).isEqualTo(name);
    }
}
