package de.tiktak.kmm.nfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NfoWriterTest {

    private final NfoWriter writer = new NfoWriter();
    private final NfoReader reader = new NfoReader();

    @TempDir
    Path directory;

    @Test
    void writesNewNfoThatReadsBackIdentically() throws IOException {
        Path nfoFile = directory.resolve("Heat.nfo");
        MovieNfo nfo = heat("Heat");

        writer.write(nfoFile, nfo);

        assertThat(reader.read(nfoFile)).isEqualTo(withSupplement(nfo, MovieNfoSupplement.EMPTY));
        String content = Files.readString(nfoFile);
        assertThat(content).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        assertThat(content).contains("<uniqueid default=\"true\" type=\"imdb\">tt0113277</uniqueid>");
        assertThat(content).contains("\n    <title>Heat</title>", "<edition>Director's Cut</edition>",
                "<videosource>bluray</videosource>");
    }

    @Test
    void writesUmlautsAsUtf8() throws IOException {
        Path nfoFile = directory.resolve("Heat.nfo");

        writer.write(nfoFile, heat("Ärger über Öl"));

        assertThat(Files.readString(nfoFile, StandardCharsets.UTF_8)).contains("<title>Ärger über Öl</title>");
    }

    @Test
    void keepsUnmanagedElementsOfExistingNfo() throws IOException {
        Path nfoFile = copyFixture("full-movie.nfo");

        writer.write(nfoFile, heat("Heat (Director's Cut)"));

        String content = Files.readString(nfoFile);
        assertThat(content).contains("<title>Heat (Director's Cut)</title>");
        assertThat(content).contains("<name>Al Pacino</name>", "<name>Robert De Niro</name>");
        assertThat(content).contains("<rating default=\"true\" max=\"10\" name=\"imdb\">");
        assertThat(content).contains("<codec>h264</codec>", "<playcount>2</playcount>", "<director>Michael Mann</director>");
        assertThat(content).doesNotContain("<!--");
    }

    @Test
    void replacesListsAndRemovesClearedFields() throws IOException {
        Path nfoFile = copyFixture("full-movie.nfo");
        MovieNfo changed = new MovieNfo("Heat", null, null, 1995, null, null, null, null, null, null, null, null,
                List.of("Action"), Map.of("tmdb", "949"));

        writer.write(nfoFile, changed);

        String content = Files.readString(nfoFile);
        assertThat(content).doesNotContain("<genre>Krimi</genre>", "<tagline>", "<plot>", "<premiered>", "tt0113277");
        assertThat(content).contains("<genre>Action</genre>", "<uniqueid default=\"true\" type=\"tmdb\">949</uniqueid>");
        assertThat(reader.read(nfoFile)).usingRecursiveComparison().ignoringFields("supplement").isEqualTo(changed);
    }

    @Test
    void writesSupplementThatReadsBackIdentically() throws IOException {
        Path nfoFile = directory.resolve("Heat.nfo");
        MovieNfo nfo = withSupplement(heat("Heat"), supplement());

        writer.write(nfoFile, nfo);

        assertThat(reader.read(nfoFile)).isEqualTo(nfo);
        assertThat(Files.readString(nfoFile)).contains(
                "<rating default=\"true\" max=\"10\" name=\"imdb\">", "<value>8.3</value>",
                "<lastplayed>2024-05-01 20:15:00</lastplayed>");
    }

    @Test
    void replacesSupplementOfExistingNfoAndKeepsTheRest() throws IOException {
        Path nfoFile = copyFixture("full-movie.nfo");

        writer.write(nfoFile, withSupplement(heat("Heat"), supplement()));

        String content = Files.readString(nfoFile);
        assertThat(content).contains("<name>Val Kilmer</name>", "<codec>h264</codec>");
        assertThat(content).doesNotContain("Al Pacino", "<studio>Warner Bros.</studio>", "<userrating>");
        assertThat(content).containsOnlyOnce("<ratings>");
    }

    @Test
    void emptySupplementRemovesTheElementsButKeepsUnknownStreams() throws IOException {
        Path nfoFile = copyFixture("full-movie.nfo");

        writer.write(nfoFile, withSupplement(heat("Heat"), MovieNfoSupplement.EMPTY));

        MovieNfoSupplement read = reader.read(nfoFile).supplement();
        assertThat(read.actors()).isEmpty();
        assertThat(read.ratings()).isEmpty();
        assertThat(read.set()).isNull();
        assertThat(read.streams().videos()).hasSize(1);
        assertThat(Files.readString(nfoFile)).contains("<codec>h264</codec>");
    }

    @Test
    void writesOnlyOneElementPerSingleValuedField() throws IOException {
        Path nfoFile = Files.writeString(directory.resolve("Heat.nfo"),
                "<movie><title>Alt</title><title>Doppelt</title></movie>");

        writer.write(nfoFile, heat("Heat"));

        assertThat(Files.readString(nfoFile)).containsOnlyOnce("<title>");
    }

    @Test
    void writesYearDerivedFromPremiered() throws IOException {
        Path nfoFile = directory.resolve("Heat.nfo");
        MovieNfo nfo = new MovieNfo("Heat", null, null, null, LocalDate.of(1995, 12, 15), null, null, null, null,
                null, null, null, List.of(), Map.of());

        writer.write(nfoFile, nfo);

        assertThat(Files.readString(nfoFile)).contains("<year>1995</year>", "<premiered>1995-12-15</premiered>");
    }

    @Test
    void replacesUrlOnlyNfoWithXml() throws IOException {
        Path nfoFile = Files.writeString(directory.resolve("Heat.nfo"), "https://www.imdb.com/title/tt0113277/");

        writer.write(nfoFile, heat("Heat"));

        assertThat(reader.read(nfoFile).title()).isEqualTo("Heat");
    }

    @Test
    void replacesMalformedNfo() throws IOException {
        Path nfoFile = Files.writeString(directory.resolve("Heat.nfo"), "<movie><title>kaputt</movie>");

        writer.write(nfoFile, heat("Heat"));

        assertThat(reader.read(nfoFile).title()).isEqualTo("Heat");
    }

    @Test
    void reindentsExistingNfoWithoutBlankLines() throws IOException {
        Path nfoFile = copyFixture("full-movie.nfo");

        writer.write(nfoFile, heat("Heat"));

        assertThat(Files.readString(nfoFile)).doesNotContainPattern("\\n\\s*\\n");
    }

    @Test
    void leavesNoTemporaryFileBehind() throws IOException {
        writer.write(directory.resolve("Heat.nfo"), heat("Heat"));

        try (var files = Files.list(directory)) {
            assertThat(files).extracting(file -> file.getFileName().toString()).containsExactly("Heat.nfo");
        }
    }

    @Test
    void reportsUnwritableTarget() {
        Path nfoFile = directory.resolve("missing-directory").resolve("Heat.nfo");

        assertThatThrownBy(() -> writer.write(nfoFile, heat("Heat")))
                .isInstanceOf(NfoException.class)
                .hasMessageContaining("kann nicht geschrieben werden");
    }

    private static MovieNfo heat(String title) {
        Map<String, String> ids = new LinkedHashMap<>();
        ids.put("imdb", "tt0113277");
        ids.put("tmdb", "949");
        return new MovieNfo(title, "Heat", "Heat", 1995, LocalDate.of(1995, 12, 15), "Handlung", "Kurz",
                "A Los Angeles Crime Saga", 170, "FSK 16", "Director's Cut", "bluray", List.of("Krimi", "Drama"), ids);
    }

    private static MovieNfo withSupplement(MovieNfo nfo, MovieNfoSupplement supplement) {
        return new MovieNfo(nfo.title(), nfo.originalTitle(), nfo.sortTitle(), nfo.year(), nfo.premiered(), nfo.plot(),
                nfo.outline(), nfo.tagline(), nfo.runtime(), nfo.mpaa(), nfo.edition(), nfo.videoSource(), nfo.genres(),
                nfo.uniqueIds(), supplement);
    }

    private static MovieNfoSupplement supplement() {
        return new MovieNfoSupplement(
                List.of(new NfoActor("Val Kilmer", "Chris Shiherlis", 0, null), new NfoActor("Jon Voight", null, null,
                        "https://image.example.org/voight.jpg")),
                List.of("Michael Mann"), List.of("Michael Mann"), List.of("Regency"), List.of("USA"),
                List.of("Heist", "Los Angeles"), List.of(new NfoRating("imdb", 8.3, 10, 650000, true),
                        new NfoRating("themoviedb", 7.9, 10, null, false)),
                new NfoMovieSet("Michael Mann Collection", null), null, 3, LocalDateTime.of(2024, 5, 1, 20, 15),
                LocalDateTime.of(2016, 11, 30, 1, 20, 15), StreamDetails.EMPTY);
    }

    private Path copyFixture(String name) throws IOException {
        Path target = directory.resolve(name);
        try (InputStream input = getClass().getResourceAsStream("/nfo/" + name)) {
            Files.copy(input, target);
        }
        return target;
    }
}
