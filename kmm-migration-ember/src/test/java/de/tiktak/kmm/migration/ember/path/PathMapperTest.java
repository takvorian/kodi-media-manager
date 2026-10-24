package de.tiktak.kmm.migration.ember.path;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PathMapperTest {

    private final PathMapper mapper = new PathMapper(List.of(
            new PathMappingRule("D:\\Filme", "/media/filme"),
            new PathMappingRule("D:\\Filme\\Kinder", "/media/kinderfilme"),
            new PathMappingRule("\\\\nas\\serien\\", "/media/serien/"),
            new PathMappingRule("E:\\", "F:\\Archiv")));

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "D:\\Filme\\Heat (1995)\\Heat.mkv         | /media/filme/Heat (1995)/Heat.mkv",
            "d:\\filme\\Heat (1995)\\Heat.mkv         | /media/filme/Heat (1995)/Heat.mkv",
            "D:/Filme/Heat (1995)/Heat.mkv            | /media/filme/Heat (1995)/Heat.mkv",
            "D:\\Filme                                | /media/filme",
            "D:\\Filme\\                              | /media/filme/",
            "\\\\nas\\serien\\Lost\\S01E01.mkv        | /media/serien/Lost/S01E01.mkv",
            "\\\\NAS\\Serien\\Lost                    | /media/serien/Lost",
            "E:\\Doku\\Erde.mkv                       | F:\\Archiv\\Doku\\Erde.mkv"
    })
    void mapsMatchingPrefix(String emberPath, String expected) {
        assertThat(mapper.map(emberPath)).contains(expected);
    }

    @Test
    void prefersLongestMatchingPrefix() {
        assertThat(mapper.map("D:\\Filme\\Kinder\\Cars (2006)\\Cars.mkv"))
                .contains("/media/kinderfilme/Cars (2006)/Cars.mkv");
    }

    @Test
    void doesNotMatchPartialDirectoryName() {
        assertThat(mapper.map("D:\\FilmeAlt\\Heat.mkv")).isEmpty();
    }

    @Test
    void returnsEmptyWhenNoRuleMatches() {
        assertThat(mapper.map("C:\\Users\\Public\\Videos\\clip.mp4")).isEmpty();
    }

    @Test
    void ignoresSurroundingWhitespace() {
        assertThat(mapper.map("  D:\\Filme\\Heat.mkv  ")).contains("/media/filme/Heat.mkv");
    }

    @Test
    void mapsPrefixOntoRootDirectory() {
        var rootMapper = new PathMapper(List.of(new PathMappingRule("D:\\", "/")));

        assertThat(rootMapper.map("D:\\")).contains("/");
        assertThat(rootMapper.map("D:\\Filme\\Heat.mkv")).contains("/Filme/Heat.mkv");
    }

    @Test
    void mapsNothingWithoutRules() {
        assertThat(new PathMapper(List.of()).map("D:\\Filme\\Heat.mkv")).isEmpty();
    }
}
