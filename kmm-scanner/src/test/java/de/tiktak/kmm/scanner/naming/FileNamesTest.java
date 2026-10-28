package de.tiktak.kmm.scanner.naming;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FileNamesTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Heat (1995)|Heat (1995)",
        "Mission: Impossible|Mission - Impossible",
        "Wer?Wie?Was*|WerWieWas",
        "\"Zitat\" <ab>|Zitat ab",
        "Tatsächlich... Liebe...|Tatsächlich... Liebe",
        "  zu   viele  Leerzeichen  |zu viele Leerzeichen",
        "AC/DC\\Live|AC-DC-Live",
        "...|''"
    })
    void makesNamesValid(String name, String expected) {
        assertThat(FileNames.clean(name)).isEqualTo(expected);
    }
}
