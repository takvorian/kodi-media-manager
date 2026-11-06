package de.tiktak.kmm.nfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TvShowNfoTest {

    private final TvShowNfoReader reader = new TvShowNfoReader();
    private final TvShowNfoWriter writer = new TvShowNfoWriter();

    @TempDir
    Path directory;

    @Test
    void readsEmberStyleShowNfoWithLegacyIds() throws IOException {
        TvShowNfo nfo = reader.read(fixture("ember-tvshow.nfo"));

        assertThat(nfo.title()).isEqualTo("Die Testserie");
        assertThat(nfo.originalTitle()).isEqualTo("The Test Show");
        assertThat(nfo.premiered()).isEqualTo(LocalDate.of(1986, 9, 22));
        assertThat(nfo.runtime()).isEqualTo(30);
        assertThat(nfo.mpaa()).isEqualTo("Germany:6");
        assertThat(nfo.status()).isEqualTo("Ended");
        assertThat(nfo.genres()).containsExactly("Komödie", "Familie");
        assertThat(nfo.studios()).containsExactly("NBC");
        assertThat(nfo.uniqueIds()).containsExactly(Map.entry("tvdb", "78020"), Map.entry("imdb", "tt0090390"),
                Map.entry("tmdb", "4658"));
        assertThat(nfo.ratings()).containsExactly(new NfoRating("tvdb", 7.3, 10, 38, true));
        assertThat(nfo.actors()).containsExactly(new NfoActor("Benji Gregory", "Brian Tanner", 0,
                "http://image.example.org/benji.jpg"), new NfoActor("Andrea Elson", "Lynn Tanner", null, null));
        assertThat(nfo.seasonNames()).containsExactly(Map.entry(0, "Specials"), Map.entry(1, "Die Ankunft"));
        assertThat(nfo.trailer()).isEqualTo(TvShowNfo.NO_TRAILER);
    }

    @Test
    void prefersUniqueIdsOverLegacyIds() throws IOException {
        Path nfoFile = Files.writeString(directory.resolve("tvshow.nfo"), """
                <tvshow><id>tt123</id><uniqueid type="TVDB" default="true">360893</uniqueid>
                <uniqueid type="tmdb">87108</uniqueid><namedseason number="x">kaputt</namedseason></tvshow>""");

        TvShowNfo nfo = reader.read(nfoFile);

        assertThat(nfo.uniqueIds()).containsExactly(Map.entry("tvdb", "360893"), Map.entry("tmdb", "87108"));
        assertThat(nfo.seasonNames()).isEmpty();
    }

    @Test
    void updatesAnExistingNfoAndKeepsUnknownElements() throws IOException {
        Path nfoFile = fixture("ember-tvshow.nfo");
        TvShowNfo changed = new TvShowNfo("Die Testserie (neu)", null, "Testserie", LocalDate.of(1986, 9, 22),
                "Neue Handlung", 25, "FSK 6", "Ended", List.of("Komödie"), List.of("NBC"), List.of("Kult"),
                Map.of("tvdb", "78020"), List.of(), List.of(new NfoActor("ALF", null, 0, null)),
                new TreeMap<>(Map.of(2, "Zweite")), 8, TvShowNfo.NO_TRAILER);

        writer.write(nfoFile, changed);

        String content = Files.readString(nfoFile);
        assertThat(content).contains("<title>Die Testserie (neu)</title>", "<sorttitle>Testserie</sorttitle>",
                "<tag>Kult</tag>", "<userrating>8</userrating>", "<namedseason number=\"2\">Zweite</namedseason>",
                "<uniqueid default=\"true\" type=\"tvdb\">78020</uniqueid>", "<language>de-DE</language>",
                "<url cache=\"78020.xml\">https://example.org/guide/78020</url>")
                .doesNotContain("<originaltitle>", "<id>78020</id>", "<imdb>", "<ratings>", "Benji Gregory");
        TvShowNfo read = reader.read(nfoFile);
        assertThat(read.language()).as("Ember's language is kept when writing").isEqualTo("de-DE");
        assertThat(read).usingRecursiveComparison().ignoringFields("language").isEqualTo(changed);
    }

    @Test
    void createsANewNfo() {
        Path nfoFile = directory.resolve("tvshow.nfo");
        TvShowNfo nfo = new TvShowNfo("Neu", null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null).withTrailer(TvShowNfo.NO_TRAILER);

        writer.write(nfoFile, nfo);

        assertThat(reader.read(nfoFile)).isEqualTo(nfo);
    }

    @Test
    void keepsWhatKmmDoesNotKnowAboutAnActor() throws IOException {
        Path nfoFile = Files.writeString(directory.resolve("tvshow.nfo"), """
                <tvshow><title>After Life</title>
                <actor><name>Ricky Gervais</name><role>Tony</role><tmdbid>17835</tmdbid><profile>x</profile></actor>
                <actor><name>Tom Basden</name><tmdbid>134229</tmdbid></actor>
                </tvshow>""");
        TvShowNfo nfo = new TvShowNfo("After Life", null, null, null, null, null, null, null, null, null, null, null,
                null, List.of(new NfoActor("Ricky Gervais", "Tony Johnson", 0, null),
                        new NfoActor("Ashley Jensen", "Emma", 1, null)), null, null);

        writer.write(nfoFile, nfo);

        String content = Files.readString(nfoFile);
        assertThat(content).contains("<role>Tony Johnson</role>", "<tmdbid>17835</tmdbid>", "<profile>x</profile>",
                "<name>Ashley Jensen</name>").doesNotContain("Tom Basden", "134229");
        assertThat(content.indexOf("<tmdbid>17835")).isGreaterThan(content.indexOf("<role>Tony Johnson"))
                .isLessThan(content.indexOf("Ashley Jensen"));
        assertThat(new TvShowNfoReader().read(nfoFile).actors()).extracting(NfoActor::name)
                .containsExactly("Ricky Gervais", "Ashley Jensen");
    }

    @Test
    void writesReplacesAndRemovesTheTrailerButKeepsAnUnknownOne() throws IOException {
        Path nfoFile = directory.resolve("tvshow.nfo");
        TvShowNfo nfo = new TvShowNfo("Lost", null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null);
        String youtube = "plugin://plugin.video.youtube/?action=play_video&videoid=KTu8iDynwNc";

        writer.write(nfoFile, nfo.withTrailer(youtube));
        assertThat(reader.read(nfoFile).trailer()).isEqualTo(youtube);
        assertThat(Files.readString(nfoFile)).contains("<trailer>" + youtube.replace("&", "&amp;") + "</trailer>");

        writer.write(nfoFile, nfo);
        assertThat(reader.read(nfoFile).trailer()).isEqualTo(youtube);

        writer.write(nfoFile, nfo.withTrailer(TvShowNfo.NO_TRAILER));
        assertThat(reader.read(nfoFile).trailer()).isEqualTo(TvShowNfo.NO_TRAILER);
        assertThat(Files.readString(nfoFile)).doesNotContain("<trailer");
    }

    @Test
    void rejectsFilesWithoutShow() throws IOException {
        Path movie = Files.writeString(directory.resolve("movie.nfo"), "<movie><title>Heat</title></movie>");

        assertThatThrownBy(() -> reader.read(movie)).isInstanceOf(NfoException.class);
        assertThatThrownBy(() -> reader.read(directory.resolve("fehlt.nfo"))).isInstanceOf(NfoException.class);
    }

    private Path fixture(String name) throws IOException {
        Path target = directory.resolve(name);
        try (InputStream input = TvShowNfoTest.class.getResourceAsStream("/nfo/" + name)) {
            Files.copy(input, target);
        }
        return target;
    }
}
