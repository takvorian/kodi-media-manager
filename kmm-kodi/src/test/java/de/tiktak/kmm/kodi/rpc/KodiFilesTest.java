package de.tiktak.kmm.kodi.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class KodiFilesTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "smb://nas/filme/Heat/Heat.mkv | smb://nas/filme/Heat/",
            "a:\\medien\\filme\\Heat\\Heat.mkv | a:\\medien\\filme\\Heat\\",
            "/media/filme/Heat/VIDEO_TS/VIDEO_TS.IFO | /media/filme/Heat/VIDEO_TS/"
    })
    void determinesTheDirectoryWithTrailingSeparator(String file, String directory) {
        assertThat(KodiFiles.directoryOf(file)).isEqualTo(directory);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "smb://nas/filme/Heat.mkv | smb://nas/filme/Heat.mkv | true",
            "smb://NAS/Filme/heat.mkv | smb://nas/filme/Heat.mkv | true",
            "smb://nas/filme/Heat.avi | smb://nas/filme/Heat.mkv | false",
            "stack://smb://nas/a/Heat-cd1.avi , smb://nas/a/Heat-cd2.avi | smb://nas/a/Heat-cd1.avi | true",
            "STACK://smb://nas/a/x,,y-cd1.avi , smb://nas/a/x,,y-cd2.avi | smb://nas/a/x,y-cd1.avi | true",
            "stack://smb://nas/a/Heat-cd1.avi , smb://nas/a/Heat-cd2.avi | smb://nas/a/Heat.avi | false"
    })
    void matchesFilesAndStacks(String kodiFile, String file, boolean expected) {
        assertThat(KodiFiles.matches(kodiFile, file)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"Heat.mkv", "''"})
    void rejectsRelativeFiles(String file) {
        assertThatThrownBy(() -> KodiFiles.directoryOf(file)).isInstanceOf(IllegalArgumentException.class);
    }
}
