package de.tiktak.kmm.nfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NfoReaderTest {

    private final NfoReader reader = new NfoReader();

    @TempDir
    Path directory;

    @Test
    void readsAllManagedFields() throws IOException {
        MovieNfo nfo = reader.read(copyFixture("full-movie.nfo"));

        assertThat(nfo.title()).isEqualTo("Heat");
        assertThat(nfo.originalTitle()).isEqualTo("Heat");
        assertThat(nfo.sortTitle()).isEqualTo("Heat");
        assertThat(nfo.year()).isEqualTo(1995);
        assertThat(nfo.premiered()).isEqualTo(LocalDate.of(1995, 12, 15));
        assertThat(nfo.plot()).startsWith("Der Berufsverbrecher");
        assertThat(nfo.outline()).isEqualTo("Ein Meisterdieb und ein Polizist.");
        assertThat(nfo.tagline()).isEqualTo("A Los Angeles Crime Saga");
        assertThat(nfo.runtime()).isEqualTo(170);
        assertThat(nfo.mpaa()).isEqualTo("FSK 16");
        assertThat(nfo.edition()).isEqualTo("Remastered");
        assertThat(nfo.videoSource()).isEqualTo("bluray");
        assertThat(nfo.genres()).containsExactly("Krimi", "Drama", "Thriller");
        assertThat(nfo.uniqueIds()).containsEntry("imdb", "tt0113277").containsEntry("tmdb", "949");
    }

    @Test
    void readsSupplement() throws IOException {
        MovieNfoSupplement supplement = reader.read(copyFixture("full-movie.nfo")).supplement();

        assertThat(supplement.actors()).containsExactly(
                new NfoActor("Al Pacino", "Lt. Vincent Hanna", 0, "https://image.example.org/pacino.jpg"),
                new NfoActor("Robert De Niro", "Neil McCauley", 1, null));
        assertThat(supplement.directors()).containsExactly("Michael Mann");
        assertThat(supplement.writers()).containsExactly("Michael Mann");
        assertThat(supplement.studios()).containsExactly("Warner Bros.");
        assertThat(supplement.countries()).containsExactly("USA");
        assertThat(supplement.tags()).containsExactly("Heist");
        assertThat(supplement.ratings()).containsExactly(new NfoRating("imdb", 8.3, 10, 650000, true));
        assertThat(supplement.set()).isEqualTo(new NfoMovieSet("Michael Mann Collection", "Filme von Michael Mann"));
        assertThat(supplement.userRating()).isEqualTo(9);
        assertThat(supplement.playCount()).isEqualTo(2);
        assertThat(supplement.lastPlayed()).isEqualTo(LocalDateTime.of(2024, 5, 1, 20, 15));
        assertThat(supplement.dateAdded()).isEqualTo(LocalDateTime.of(2016, 11, 30, 1, 20, 15));
    }

    @Test
    void readsTheTmdbIdOfASetLikeEmberWritesIt() throws IOException {
        Path nfo = write("<movie><set><name>Matrix Filmreihe</name><tmdb>2344</tmdb></set></movie>");

        assertThat(reader.read(nfo).supplement().set()).isEqualTo(new NfoMovieSet("Matrix Filmreihe", null, "2344"));
    }

    @Test
    void readsLegacySetNameAndDateWithoutTime() throws IOException {
        MovieNfoSupplement supplement = reader.read(write(
                "<movie><set> Matrix Collection </set><dateadded>2016-11-30</dateadded></movie>")).supplement();

        assertThat(supplement.set()).isEqualTo(new NfoMovieSet("Matrix Collection", null));
        assertThat(supplement.dateAdded()).isEqualTo(LocalDateTime.of(2016, 11, 30, 0, 0));
    }

    @Test
    void readsRatingsWithDecimalCommaAndGroupedVotes() throws IOException {
        MovieNfoSupplement supplement = reader.read(write("""
                <movie><ratings>
                  <rating name="themoviedb"><value>7,5</value><votes>12.345</votes></rating>
                  <rating name="leer"><value>-</value></rating>
                  <rating><value>5</value></rating>
                </ratings></movie>
                """)).supplement();

        assertThat(supplement.ratings()).containsExactly(new NfoRating("themoviedb", 7.5, null, 12345, false));
    }

    @Test
    void ignoresMalformedSupplementValues() throws IOException {
        MovieNfoSupplement supplement = reader.read(write("""
                <movie><actor><role>ohne Namen</role></actor><set><name> </name></set>
                <playcount>oft</playcount><userrating>99999999999</userrating>
                <lastplayed>gestern</lastplayed><dateadded>2016</dateadded></movie>
                """)).supplement();

        assertThat(supplement).isEqualTo(MovieNfoSupplement.EMPTY.withTrailer(MovieNfoSupplement.NO_TRAILER));
    }

    @Test
    void readsMinimalNfoAndTreatsBlankValuesAsMissing() throws IOException {
        MovieNfo nfo = reader.read(write("<movie><title> Heat </title><plot>   </plot></movie>"));

        assertThat(nfo.title()).isEqualTo("Heat");
        assertThat(nfo.plot()).isNull();
        assertThat(nfo.year()).isNull();
        assertThat(nfo.genres()).isEmpty();
        assertThat(nfo.uniqueIds()).isEmpty();
    }

    @Test
    void derivesYearFromPremieredWhenYearIsMissing() throws IOException {
        MovieNfo nfo = reader.read(write("<movie><premiered>1995-12-15</premiered></movie>"));

        assertThat(nfo.year()).isNull();
        assertThat(nfo.effectiveYear()).isEqualTo(1995);
    }

    @Test
    void ignoresMalformedNumbersAndDates() throws IOException {
        MovieNfo nfo = reader.read(write(
                "<movie><year>neunzehn</year><premiered>15.12.1995</premiered><runtime>lang</runtime></movie>"));

        assertThat(nfo.year()).isNull();
        assertThat(nfo.premiered()).isNull();
        assertThat(nfo.runtime()).isNull();
    }

    @Test
    void readsRuntimeWithUnit() throws IOException {
        assertThat(reader.read(write("<movie><runtime>120 min</runtime></movie>")).runtime()).isEqualTo(120);
    }

    @Test
    void readsDateWithTimePart() throws IOException {
        assertThat(reader.read(write("<movie><premiered>1995-12-15 00:00:00</premiered></movie>")).premiered())
                .isEqualTo(LocalDate.of(1995, 12, 15));
    }

    @Test
    void readsLegacyIdElements() throws IOException {
        MovieNfo nfo = reader.read(write("<movie><id>tt0113277</id><tmdbid>949</tmdbid></movie>"));

        assertThat(nfo.uniqueIds()).containsEntry("imdb", "tt0113277").containsEntry("tmdb", "949");
    }

    @Test
    void ignoresLegacyIdThatIsNoImdbId() throws IOException {
        assertThat(reader.read(write("<movie><id>12345</id></movie>")).uniqueIds()).isEmpty();
    }

    @Test
    void ignoresUniqueIdsWithoutTypeOrValue() throws IOException {
        MovieNfo nfo = reader.read(write(
                "<movie><uniqueid>tt0113277</uniqueid><uniqueid type=\"tmdb\"> </uniqueid></movie>"));

        assertThat(nfo.uniqueIds()).isEmpty();
    }

    @Test
    void readsUrlOnlyNfo() throws IOException {
        MovieNfo nfo = reader.read(write("https://www.imdb.com/title/tt0113277/\n"));

        assertThat(nfo.title()).isNull();
        assertThat(nfo.uniqueId("imdb")).contains("tt0113277");
        assertThat(nfo.supplement()).isEqualTo(MovieNfoSupplement.EMPTY);
    }

    @Test
    void readsTmdbUrl() throws IOException {
        assertThat(reader.read(write("https://www.themoviedb.org/movie/949-heat")).uniqueId("TMDB")).contains("949");
    }

    @Test
    void readsCombinedNfoWithUrlAfterXml() throws IOException {
        MovieNfo nfo = reader.read(write("""
                <?xml version="1.0" encoding="UTF-8"?>
                <movie><title>Heat</title></movie>
                https://www.imdb.com/title/tt0113277/
                """));

        assertThat(nfo.title()).isEqualTo("Heat");
        assertThat(nfo.uniqueIds()).containsEntry("imdb", "tt0113277");
    }

    @Test
    void prefersUniqueIdOverUrl() throws IOException {
        MovieNfo nfo = reader.read(write("""
                <movie><uniqueid type="imdb">tt0113277</uniqueid></movie>
                https://www.imdb.com/title/tt9999999/
                """));

        assertThat(nfo.uniqueIds()).containsEntry("imdb", "tt0113277");
    }

    @Test
    void rejectsMalformedXml() throws IOException {
        Path nfoFile = write("<movie><title>Heat</movie>");

        assertThatThrownBy(() -> reader.read(nfoFile))
                .isInstanceOf(NfoException.class)
                .hasMessageContaining("kann nicht gelesen werden");
    }

    @Test
    void rejectsDocumentTypeDeclarations() throws IOException {
        Path secret = Files.writeString(directory.resolve("secret.txt"), "geheim");
        Path nfoFile = write("""
                <!DOCTYPE movie [<!ENTITY xxe SYSTEM "%s">]>
                <movie><title>&xxe;</title></movie>
                """.formatted(secret.toUri()));

        assertThatThrownBy(() -> reader.read(nfoFile)).isInstanceOf(NfoException.class);
    }

    @Test
    void rejectsMissingFile() {
        assertThatThrownBy(() -> reader.read(directory.resolve("missing.nfo"))).isInstanceOf(NfoException.class);
    }

    private Path copyFixture(String name) throws IOException {
        Path target = directory.resolve(name);
        try (InputStream input = getClass().getResourceAsStream("/nfo/" + name)) {
            Files.copy(input, target);
        }
        return target;
    }

    private Path write(String content) throws IOException {
        return Files.writeString(directory.resolve("movie.nfo"), content);
    }
}
