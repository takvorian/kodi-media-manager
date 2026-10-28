package de.tiktak.kmm.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.job.JobTask;
import de.tiktak.kmm.movie.MovieTagService;
import de.tiktak.kmm.tvshow.TvShowTagService;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private MovieTagService movieTags;

    @Mock
    private TvShowTagService showTags;

    @Mock
    private JobService jobService;

    @InjectMocks
    private TagService service;

    @Test
    void listsTagsOfMoviesAndShowsSortedByName() {
        when(movieTags.countByTag()).thenReturn(Map.of("Weihnachten", 2L, "bud spencer", 1L));
        when(showTags.countByTag()).thenReturn(Map.of("Weihnachten", 1L, "Anime", 3L));

        assertThat(service.findAll()).containsExactly(new TagSummary("Anime", 0, 3),
                new TagSummary("bud spencer", 1, 0), new TagSummary("Weihnachten", 2, 1));
    }

    @Test
    void listsTheTaggedItems() {
        Map<Long, String> movies = new LinkedHashMap<>();
        movies.put(4L, "Alien");
        movies.put(2L, "Heat");
        when(movieTags.findTitlesByTag("Krimi")).thenReturn(movies);
        when(showTags.findTitlesByTag("Krimi")).thenReturn(Map.of(7L, "Monk"));

        assertThat(service.findTagged("Krimi")).isEqualTo(new TaggedItems("Krimi",
                List.of(new TaggedItems.Item(4, "Alien"), new TaggedItems.Item(2, "Heat")),
                List.of(new TaggedItems.Item(7, "Monk"))));
    }

    @Test
    void renamesATagAndMergesItWithAnExistingOne() {
        when(movieTags.findIdsByTag("Spencer")).thenReturn(List.of(1L));
        when(showTags.findIdsByTag("Spencer")).thenReturn(List.of());

        UnaryOperator<List<String>> change = submitted(() -> service.rename("Spencer", " Bud Spencer "));

        assertThat(change.apply(List.of("Western", "Spencer", "Bud Spencer"))).containsExactly("Western", "Bud Spencer");
        assertThat(change.apply(List.of("Spencer"))).containsExactly("Bud Spencer");
    }

    @Test
    void deletesATagEverywhere() {
        when(movieTags.findIdsByTag("Alt")).thenReturn(List.of(1L));
        when(showTags.findIdsByTag("Alt")).thenReturn(List.of(2L));

        UnaryOperator<List<String>> change = submitted(() -> service.delete("Alt"));

        assertThat(change.apply(List.of("Alt", "Neu"))).containsExactly("Neu");
    }

    @Test
    void assignsANewTagOnceAndRemovesIt() {
        UnaryOperator<List<String>> assign = submitted(() -> service.assign(new TagAssignment(" Karl May ",
                List.of(1L, 1L), List.of(3L))));
        assertThat(assign.apply(List.of("Western"))).containsExactly("Western", "Karl May");
        assertThat(assign.apply(List.of("Karl May"))).containsExactly("Karl May");

        UnaryOperator<List<String>> unassign = submitted(() -> service.unassign(new TagAssignment("Karl May",
                List.of(1L), List.of())));
        assertThat(unassign.apply(List.of("Karl May", "Western"))).containsExactly("Western");
    }

    @Test
    void rejectsEmptyAndTooLongNames() {
        assertThatThrownBy(() -> service.assign(new TagAssignment("  ", List.of(), List.of())))
                .isInstanceOf(ErrorResponseException.class);
        assertThatThrownBy(() -> service.rename("a", "x".repeat(TagNames.MAX_LENGTH + 1)))
                .isInstanceOf(ErrorResponseException.class);
        verify(jobService, never()).submit(any(), any(), any());
    }

    @Test
    void countsChangesAndFailuresOfMoviesAndShows() {
        UnaryOperator<List<String>> change = tags -> tags;
        when(movieTags.changeTags(1L, change)).thenReturn(true);
        when(movieTags.changeTags(2L, change)).thenReturn(false);
        when(movieTags.changeTags(3L, change)).thenThrow(new ErrorResponseException(HttpStatus.UNPROCESSABLE_CONTENT));
        when(showTags.changeTags(7L, change)).thenReturn(true);
        JobContext context = mock(JobContext.class);

        TagChangeResult result = service.apply("Test", List.of(1L, 2L, 3L), List.of(7L), change, context);

        assertThat(result.changed()).isEqualTo(2);
        assertThat(result.unchanged()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.failures()).singleElement().asString().startsWith("Film 3: ");
        verify(context).reportProgress(3, 4, "Serie 7");
    }

    @SuppressWarnings("unchecked")
    private UnaryOperator<List<String>> submitted(Runnable start) {
        ArgumentCaptor<JobTask<TagChangeResult>> task = ArgumentCaptor.forClass(JobTask.class);
        start.run();
        verify(jobService, atLeastOnce()).submit(eq(TagService.JOB_KEY), any(), task.capture());
        List<UnaryOperator<List<String>>> changes = new ArrayList<>();
        lenient().when(movieTags.changeTags(anyLong(), any())).thenAnswer(invocation -> {
            changes.add(invocation.getArgument(1));
            return true;
        });
        lenient().when(showTags.changeTags(anyLong(), any())).thenAnswer(invocation -> {
            changes.add(invocation.getArgument(1));
            return true;
        });
        task.getValue().run(mock(JobContext.class));
        return changes.isEmpty() ? tags -> tags : changes.getFirst();
    }
}
