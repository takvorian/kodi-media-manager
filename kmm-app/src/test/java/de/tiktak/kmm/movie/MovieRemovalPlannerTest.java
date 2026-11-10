package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.movie.MovieRemoval.RemovedMovie;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.removal.RemovalPlan;
import de.tiktak.kmm.scanner.DiscFormat;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.source.MediaSourceService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MovieRemovalPlannerTest {

    private static final long SOURCE_ID = 2;

    @TempDir
    Path source;

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private MediaSourceService sourceService;

    private MovieRemovalPlanner planner;
    private final List<Movie> movies = new ArrayList<>();

    @BeforeEach
    void setUp() {
        planner = new MovieRemovalPlanner(movieRepository, sourceService);
        when(sourceService.require(SOURCE_ID)).thenReturn(new MediaSource("Filme", source.toString(),
                MediaType.MOVIE));
        when(movieRepository.findBySourceId(SOURCE_ID)).thenReturn(movies);
    }

    @Test
    void plansAMovieInItsOwnFolderWithAllItsFiles() throws IOException {
        Path folder = source.resolve("Heat (1995)");
        Path video = file(folder.resolve("Heat.mkv"));
        Path nfo = file(folder.resolve("Heat.nfo"));
        Path poster = file(folder.resolve("Heat-poster.jpg"));
        Path subtitle = file(folder.resolve("Heat.de.srt"));
        Path folderNfo = file(folder.resolve("movie.nfo"));
        movie(1, video, "Heat", DiscFormat.FILE, 1);

        MovieRemoval removal = planner.movies(List.of(1L));
        RemovalPlan plan = removal.plan();

        assertThat(plan.files()).containsExactlyInAnyOrder(video.toString(), nfo.toString(), poster.toString(),
                subtitle.toString(), folderNfo.toString());
        assertThat(plan.directories()).containsExactly(folder.toString());
        assertThat(plan.notes()).isEmpty();
        assertThat(plan.entries()).containsExactly(LocalizedText.literal("Heat (1995)"));
        assertThat(removal.movies()).containsExactly(new RemovedMovie(1, "Heat", video.toString()));
    }

    @Test
    void notesFilesThatKeepTheFolder() throws IOException {
        Path folder = source.resolve("Heat");
        Path video = file(folder.resolve("Heat.mkv"));
        file(folder.resolve("extrafanart/fanart1.jpg"));
        movie(1, video, "Heat", DiscFormat.FILE, 1);

        RemovalPlan plan = planner.movies(List.of(1L)).plan();

        assertThat(plan.directories()).isEmpty();
        assertThat(plan.notes()).singleElement().satisfies(note -> {
            assertThat(note.key()).isEqualTo("removal.note.folderKept");
            assertThat(note.args()).containsEntry("count", 1);
        });
    }

    @Test
    void leavesTheFilesOfOtherMoviesInTheSameFolderAlone() throws IOException {
        Path heat = file(source.resolve("Heat.mkv"));
        Path heatNfo = file(source.resolve("Heat.nfo"));
        Path heat2 = file(source.resolve("Heat 2.mkv"));
        file(source.resolve("Heat 2.nfo"));
        file(source.resolve("movie.nfo"));
        movie(1, heat, "Heat", DiscFormat.FILE, 1);
        movie(2, heat2, "Heat 2", DiscFormat.FILE, 1);

        RemovalPlan plan = planner.movies(List.of(1L)).plan();

        assertThat(plan.files()).containsExactly(heat.toString(), heatNfo.toString());
        assertThat(plan.notes()).isEmpty();
        assertThat(plan.canDeleteFiles()).isTrue();
    }

    @Test
    void plansAllPartsOfAMultiPartMovie() throws IOException {
        Path folder = source.resolve("Heat");
        Path first = file(folder.resolve("Heat-cd1.avi"));
        Path second = file(folder.resolve("Heat-cd2.avi"));
        Path nfo = file(folder.resolve("Heat.nfo"));
        Path emberNfo = file(folder.resolve("Heat-cd1.nfo"));
        movie(1, first, "Heat", DiscFormat.FILE, 2);

        RemovalPlan plan = planner.movies(List.of(1L)).plan();

        assertThat(plan.files()).containsExactlyInAnyOrder(first.toString(), second.toString(), nfo.toString(),
                emberNfo.toString());
        assertThat(plan.directories()).containsExactly(folder.toString());
    }

    @Test
    void plansTheWholeFolderOfADisc() throws IOException {
        Path folder = source.resolve("Heat");
        Path marker = file(folder.resolve("VIDEO_TS/VIDEO_TS.IFO"));
        Path vob = file(folder.resolve("VIDEO_TS/VTS_01_1.VOB"));
        Path poster = file(folder.resolve("poster.jpg"));
        movie(1, marker, "Heat", DiscFormat.DVD, 1);

        RemovalPlan plan = planner.movies(List.of(1L)).plan();

        assertThat(plan.files()).containsExactlyInAnyOrder(marker.toString(), vob.toString(), poster.toString());
        assertThat(plan.directories()).containsExactly(folder.toString(), folder.resolve("VIDEO_TS").toString());
    }

    @Test
    void refusesToDeleteADiscFolderSharedWithOtherMovies() throws IOException {
        Path marker = file(source.resolve("Heat/BDMV/index.bdmv"));
        movie(1, marker, "Heat", DiscFormat.BLURAY, 1);
        movie(2, file(source.resolve("Heat/Extras/Making of.mkv")), "Making of", DiscFormat.FILE, 1);

        RemovalPlan plan = planner.movies(List.of(1L)).plan();

        assertThat(plan.canDeleteFiles()).isFalse();
        assertThat(plan.files()).isEmpty();
        assertThat(plan.notes()).extracting(LocalizedText::key).containsExactly("removal.note.sharedDiscFolder");
    }

    @Test
    void combinesASelection() throws IOException {
        Path heat = file(source.resolve("Heat.mkv"));
        Path ronin = file(source.resolve("Ronin/Ronin.mkv"));
        movie(1, heat, "Heat", DiscFormat.FILE, 1);
        movie(2, ronin, "Ronin", DiscFormat.FILE, 1);

        MovieRemoval removal = planner.movies(List.of(2L, 1L, 2L));

        assertThat(removal.movieIds()).containsExactly(2L, 1L);
        assertThat(removal.plan().files()).containsExactly(heat.toString(), ronin.toString());
    }

    @Test
    void rejectsUnknownMovies() {
        when(movieRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> planner.movies(List.of(9L))).isInstanceOf(MovieNotFoundException.class);
    }

    private void movie(long id, Path file, String title, DiscFormat format, int parts) {
        Movie movie = new Movie(SOURCE_ID, file.toString(), title, title.equals("Heat") ? 1995 : null, format, parts);
        ReflectionTestUtils.setField(movie, "id", id);
        movies.add(movie);
        when(movieRepository.findById(id)).thenReturn(Optional.of(movie));
    }

    private static Path file(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, "x");
    }
}
