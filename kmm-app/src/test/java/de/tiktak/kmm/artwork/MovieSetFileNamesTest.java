package de.tiktak.kmm.artwork;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MovieSetFileNamesTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            Matrix Filmreihe           | Matrix Filmreihe
            Star Wars: Filmreihe       | Star Wars- Filmreihe
            AC/DC? "Live" <*>          | AC-DC- -Live- ---
            Mr. & Mrs.                 | Mr. & Mrs.
            """)
    void replacesCharactersThatAreInvalidInWindowsFileNamesLikeEmber(String title, String expected) {
        assertThat(MovieSetFileNames.flat(title)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            Matrix Filmreihe           | Matrix Filmreihe
            Star Wars: Filmreihe       | Star Wars_ Filmreihe
            AC/DC? "Live" <*>          | AC_DC_ _Live_ ___
            Mr. & Mrs.                 | Mr. & Mrs
            """)
    void namesTheSetFolderLikeKodi(String title, String expected) {
        assertThat(MovieSetFileNames.kodiFolder(title)).isEqualTo(expected);
    }
}
