package de.tiktak.kmm.kodi.path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class KodiPathMapperTest {

    private final KodiPathMapper mapper = new KodiPathMapper(List.of(
            new KodiPathMapping("a:\\medien\\filme", "smb://YGGDRASIL/medien/filme/"),
            new KodiPathMapping("a:\\medien\\filme\\kinder\\", "nfs://nas/kinder"),
            new KodiPathMapping("/media/serien/", "/storage/serien"),
            new KodiPathMapping("/media/doku", "D:\\Doku")));

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "a:\\medien\\filme\\Heat (1995)\\Heat (1995).mkv | smb://YGGDRASIL/medien/filme/Heat (1995)/Heat (1995).mkv",
            "A:\\Medien\\Filme\\Heat.mkv | smb://YGGDRASIL/medien/filme/Heat.mkv",
            "a:/medien/filme | smb://YGGDRASIL/medien/filme",
            "a:\\medien\\filme\\Kinder\\Cars.mkv | nfs://nas/kinder/Cars.mkv",
            "/media/serien/Dark/S01E01.mkv | /storage/serien/Dark/S01E01.mkv",
            "/media/doku/Erde/Erde.mkv | D:\\Doku\\Erde\\Erde.mkv"
    })
    void mapsWithTheLongestMatchingPrefix(String local, String kodi) {
        assertThat(mapper.toKodiPath(local)).contains(kodi);
    }

    @ParameterizedTest
    @CsvSource({"a:\\medien\\filmeneu\\Heat.mkv", "/Media/serien/Dark.mkv", "/media", "b:\\filme\\Heat.mkv"})
    void ignoresPathsWithoutMatchingMapping(String local) {
        assertThat(mapper.toKodiPath(local)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "a:\\medien\\filme\\Heat (1995) | smb://YGGDRASIL/medien/filme/Heat (1995)/",
            "a:\\medien\\filme\\ | smb://YGGDRASIL/medien/filme/",
            "/media/doku/Erde | D:\\Doku\\Erde\\"
    })
    void mapsDirectoriesWithTrailingSeparator(String local, String kodi) {
        assertThat(mapper.toKodiDirectory(local)).contains(kodi);
    }

    @Test
    void rejectsBlankMappings() {
        assertThatThrownBy(() -> new KodiPathMapping(" ", "smb://nas/")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KodiPathMapping("/media", null)).isInstanceOf(IllegalArgumentException.class);
    }
}
