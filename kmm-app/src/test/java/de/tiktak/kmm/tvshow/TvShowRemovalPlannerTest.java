package de.tiktak.kmm.tvshow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.removal.RemovalPlan;
import de.tiktak.kmm.removal.RemovalProblemException;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.source.MediaSourceService;
import de.tiktak.kmm.tvshow.EpisodesRemovedEvent.RemovedEpisode;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TvShowRemovalPlannerTest {

    private static final long SOURCE_ID = 3;
    private static final long SHOW_ID = 7;

    @TempDir
    Path source;

    @Mock
    private TvShowRepository showRepository;

    @Mock
    private EpisodeRepository episodeRepository;

    @Mock
    private MediaSourceService sourceService;

    private TvShowRemovalPlanner planner;
    private Path showDirectory;
    private TvShow show;

    @BeforeEach
    void setUp() {
        planner = new TvShowRemovalPlanner(showRepository, episodeRepository, sourceService);
        when(sourceService.require(SOURCE_ID)).thenReturn(new MediaSource("Serien", source.toString(),
                MediaType.TV_SHOW));
        showDirectory = source.resolve("ALF");
        show = new TvShow(SOURCE_ID, showDirectory.toString(), "ALF", 1986);
        ReflectionTestUtils.setField(show, "id", SHOW_ID);
        when(showRepository.findById(SHOW_ID)).thenReturn(Optional.of(show));
    }

    @Test
    void plansAnEpisodeWithItsCompanionFilesAndLeavesOtherFilesAlone() throws IOException {
        Path season = showDirectory.resolve("Staffel 1");
        Path video = file(season.resolve("ALF S01E01.avi"));
        Path nfo = file(season.resolve("ALF S01E01.nfo"));
        Path thumb = file(season.resolve("ALF S01E01-thumb.jpg"));
        Path subtitle = file(season.resolve("ALF S01E01.de.srt"));
        file(season.resolve("ALF S01E01 Extra.avi"));
        file(season.resolve("ALF S01E01 Extra.nfo"));
        file(season.resolve("ALF S01E02.avi"));
        Episode first = episode(1, video, 1, 1);
        episodes(first, episode(2, season.resolve("ALF S01E01 Extra.avi"), 1, 2),
                episode(3, season.resolve("ALF S01E02.avi"), 1, 3));

        TvShowRemoval removal = planner.episode(1);
        RemovalPlan plan = removal.plan();

        assertThat(plan.files()).containsExactlyInAnyOrder(video.toString(), nfo.toString(), thumb.toString(),
                subtitle.toString());
        assertThat(plan.directories()).isEmpty();
        assertThat(plan.notes()).isEmpty();
        assertThat(plan.canDeleteFiles()).isTrue();
        assertThat(plan.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.key()).isEqualTo("removal.entry.episode");
            assertThat(entry.args()).containsEntry("show", "ALF").containsEntry("code", "S01E01")
                    .containsEntry("title", "Episode 1");
        });
        assertThat(removal.subject()).isEqualTo("ALF S01E01");
        assertThat(removal.episodeIds()).containsExactly(1L);
        assertThat(removal.wholeShow()).isFalse();
    }

    @Test
    void removesAMultiEpisodeFileWithAllItsEpisodes() throws IOException {
        Path video = file(showDirectory.resolve("ALF S01E01E02.avi"));
        Episode first = episode(1, video, 1, 1);
        Episode second = episode(2, video, 1, 2);
        episodes(first, second, episode(3, file(showDirectory.resolve("ALF S01E03.avi")), 1, 3));
        when(episodeRepository.findById(2L)).thenReturn(Optional.of(second));

        TvShowRemoval removal = planner.episode(2);

        assertThat(removal.episodeIds()).containsExactly(1L, 2L);
        assertThat(removal.episodes()).containsExactly(new RemovedEpisode(video.toString(), 1, 1),
                new RemovedEpisode(video.toString(), 1, 2));
        assertThat(removal.subject()).isEqualTo("ALF S01E01E02");
        RemovalPlan plan = removal.plan();
        assertThat(plan.entries()).hasSize(2);
        assertThat(plan.files()).containsExactly(video.toString());
        assertThat(plan.notes()).singleElement().satisfies(note -> {
            assertThat(note.key()).isEqualTo("removal.note.multiEpisode");
            assertThat(note.args()).containsEntry("file", "ALF S01E01E02.avi");
        });
    }

    @Test
    void refusesToDeleteAnEpisodeOutsideTheSource(@TempDir Path elsewhere) throws IOException {
        Path video = file(elsewhere.resolve("ALF S01E01.avi"));
        episodes(episode(1, video, 1, 1));

        RemovalPlan plan = planner.episode(1).plan();

        assertThat(plan.canDeleteFiles()).isFalse();
        assertThat(plan.files()).isEmpty();
        assertThat(plan.notes()).extracting(LocalizedText::key).containsExactly("removal.note.outsideSource");
    }

    @Test
    void plansASeasonWithItsArtworkAndTheEmptiedSeasonFolder() throws IOException {
        Path season = showDirectory.resolve("Staffel 1");
        Path first = file(season.resolve("S01E01.avi"));
        Path second = file(season.resolve("S01E02.avi"));
        Path poster = file(showDirectory.resolve("season01-poster.jpg"));
        file(showDirectory.resolve("season02-poster.jpg"));
        file(showDirectory.resolve("tvshow.nfo"));
        Path shared = file(showDirectory.resolve("Staffel 2/S01E03E04.avi"));
        episodes(episode(1, first, 1, 1), episode(2, second, 1, 2), episode(3, shared, 1, 3),
                episode(4, shared, 2, 1), episode(5, file(showDirectory.resolve("Staffel 2/S02E02.avi")), 2, 2));

        TvShowRemoval removal = planner.season(SHOW_ID, 1);
        RemovalPlan plan = removal.plan();

        assertThat(removal.episodeIds()).containsExactly(1L, 2L, 3L, 4L);
        assertThat(removal.seasons()).containsExactlyInAnyOrder(1, 2);
        assertThat(removal.subject()).isEqualTo("ALF");
        assertThat(plan.files()).containsExactlyInAnyOrder(first.toString(), second.toString(), poster.toString(),
                shared.toString());
        assertThat(plan.directories()).containsExactly(season.toString());
        assertThat(plan.notes()).extracting(LocalizedText::key)
                .containsExactly("removal.note.multiEpisode", "removal.note.otherSeason");
        assertThat(plan.notes().get(1).args()).containsEntry("season", 2);
    }

    @Test
    void rejectsASeasonWithoutEpisodes() {
        episodes();

        assertThatThrownBy(() -> planner.season(SHOW_ID, 4)).isInstanceOf(RemovalProblemException.class);
    }

    @Test
    void rejectsUnknownEpisodesAndShows() {
        when(episodeRepository.findById(99L)).thenReturn(Optional.empty());
        when(showRepository.findById(98L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> planner.episode(99)).isInstanceOf(TvShowProblemException.class);
        assertThatThrownBy(() -> planner.show(98)).isInstanceOf(TvShowProblemException.class);
    }

    @Test
    void plansTheWholeShowFolderAndNamesFilesKmmDoesNotKnow() throws IOException {
        Path video = file(showDirectory.resolve("Staffel 1/S01E01.avi"));
        Path nfo = file(showDirectory.resolve("Staffel 1/S01E01.nfo"));
        Path showNfo = file(showDirectory.resolve("tvshow.nfo"));
        Path poster = file(showDirectory.resolve("poster.jpg"));
        Path theme = file(showDirectory.resolve("theme.mp3"));
        Path notes = file(showDirectory.resolve("Staffel 1/notizen.txt"));
        Path other = file(source.resolve("Lost/tvshow.nfo"));
        episodes(episode(1, video, 1, 1));

        TvShowRemoval removal = planner.show(SHOW_ID);
        RemovalPlan plan = removal.plan();

        assertThat(removal.wholeShow()).isTrue();
        assertThat(removal.directory()).isEqualTo(showDirectory.toString());
        assertThat(plan.files()).containsExactlyInAnyOrder(video.toString(), nfo.toString(), showNfo.toString(),
                poster.toString(), theme.toString(), notes.toString());
        assertThat(plan.files()).doesNotContain(other.toString());
        assertThat(plan.directories()).containsExactly(showDirectory.toString(),
                showDirectory.resolve("Staffel 1").toString());
        assertThat(plan.notes()).singleElement().satisfies(note -> {
            assertThat(note.key()).isEqualTo("removal.note.unknownFiles");
            assertThat(note.args()).containsEntry("count", 1).containsEntry("files", "notizen.txt");
        });
        assertThat(plan.entries()).singleElement().satisfies(entry -> assertThat(entry.args())
                .containsEntry("title", "ALF").containsEntry("count", 1));
    }

    @Test
    void neverDeletesAShowFolderThatIsTheSource() throws IOException {
        TvShow sourceShow = new TvShow(SOURCE_ID, source.toString(), "ALF", null);
        ReflectionTestUtils.setField(sourceShow, "id", SHOW_ID);
        when(showRepository.findById(SHOW_ID)).thenReturn(Optional.of(sourceShow));
        episodes(episode(1, file(source.resolve("S01E01.avi")), 1, 1));

        RemovalPlan plan = planner.show(SHOW_ID).plan();

        assertThat(plan.canDeleteFiles()).isFalse();
        assertThat(plan.notes()).extracting(LocalizedText::key).containsExactly("removal.note.sourceItself");
    }

    private Episode episode(long id, Path file, int season, int number) {
        Episode episode = new Episode(SHOW_ID, file.toString(), season, number, "Episode " + id);
        ReflectionTestUtils.setField(episode, "id", id);
        when(episodeRepository.findById(id)).thenReturn(Optional.of(episode));
        return episode;
    }

    private void episodes(Episode... episodes) {
        when(episodeRepository.findByTvShowIdOrderBySeasonAscEpisodeAsc(SHOW_ID)).thenReturn(List.of(episodes));
    }

    private static Path file(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, "x");
    }
}
