package de.tiktak.kmm.tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.LongPredicate;
import java.util.function.UnaryOperator;

import org.springframework.stereotype.Service;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.movie.MovieTagService;
import de.tiktak.kmm.tvshow.TvShowTagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tag-Manager wie in Ember: Tags von Filmen und Serien zählen, umbenennen, löschen, zuweisen und entfernen. Tags
 * gibt es nur an Filmen und Serien; ein Tag ohne Einträge verschwindet. Änderungen schreiben die NFOs der
 * betroffenen Einträge und laufen deshalb als Hintergrund-Job.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagService {

    static final String JOB_KEY = "tags";
    static final int MAX_LISTED_FAILURES = 20;

    private final MovieTagService movieTags;
    private final TvShowTagService showTags;
    private final JobService jobService;

    /** @return alle Tags mit der Anzahl ihrer Filme und Serien, nach Name sortiert */
    public List<TagSummary> findAll() {
        Map<String, Long> movies = movieTags.countByTag();
        Map<String, Long> shows = showTags.countByTag();
        Set<String> names = new TreeSet<>(Comparator.comparing((String name) -> name.toLowerCase(Locale.ROOT))
                .thenComparing(Comparator.naturalOrder()));
        names.addAll(movies.keySet());
        names.addAll(shows.keySet());
        return names.stream()
                .map(name -> new TagSummary(name, movies.getOrDefault(name, 0L), shows.getOrDefault(name, 0L)))
                .toList();
    }

    /** @return die Filme und Serien mit dem Tag */
    public TaggedItems findTagged(String tag) {
        return new TaggedItems(tag, items(movieTags.findTitlesByTag(tag)), items(showTags.findTitlesByTag(tag)));
    }

    /** Benennt einen Tag an allen Filmen und Serien um; trägt ein Eintrag beide Namen, bleibt einer. */
    public JobSnapshot rename(String tag, String newName) {
        String name = TagNames.clean(newName);
        return submit("Tag „%s“ in „%s“ umbenennen".formatted(tag, name), movieTags.findIdsByTag(tag),
                showTags.findIdsByTag(tag), tags -> replace(tags, tag, name));
    }

    /** Entfernt einen Tag von allen Filmen und Serien. */
    public JobSnapshot delete(String tag) {
        return submit("Tag „%s“ löschen".formatted(tag), movieTags.findIdsByTag(tag), showTags.findIdsByTag(tag),
                tags -> tags.stream().filter(existing -> !existing.equals(tag)).toList());
    }

    /** Fügt den ausgewählten Filmen und Serien einen Tag hinzu, auch einen neuen. */
    public JobSnapshot assign(TagAssignment assignment) {
        String tag = TagNames.clean(assignment.tag());
        return submit("Tag „%s“ zuweisen".formatted(tag), assignment.movieIds(), assignment.showIds(),
                tags -> tags.contains(tag) ? tags : append(tags, tag));
    }

    /** Entfernt einen Tag von den ausgewählten Filmen und Serien. */
    public JobSnapshot unassign(TagAssignment assignment) {
        String tag = assignment.tag();
        return submit("Tag „%s“ entfernen".formatted(tag), assignment.movieIds(), assignment.showIds(),
                tags -> tags.stream().filter(existing -> !existing.equals(tag)).toList());
    }

    private JobSnapshot submit(String title, Collection<Long> movieIds, Collection<Long> showIds,
                               UnaryOperator<List<String>> change) {
        List<Long> movies = List.copyOf(new LinkedHashSet<>(movieIds));
        List<Long> shows = List.copyOf(new LinkedHashSet<>(showIds));
        return jobService.submit(JOB_KEY, title, context -> apply(title, movies, shows, change, context));
    }

    TagChangeResult apply(String title, List<Long> movieIds, List<Long> showIds, UnaryOperator<List<String>> change,
                          JobContext context) {
        Tally tally = new Tally(movieIds.size() + showIds.size());
        for (long movieId : movieIds) {
            tally.change(context, "Film " + movieId, id -> movieTags.changeTags(id, change), movieId);
        }
        for (long showId : showIds) {
            tally.change(context, "Serie " + showId, id -> showTags.changeTags(id, change), showId);
        }
        TagChangeResult result = tally.result();
        log.info("{}: {} changed, {} unchanged, {} failed", title, result.changed(), result.unchanged(),
                result.failed());
        return result;
    }

    private static List<String> replace(List<String> tags, String tag, String name) {
        List<String> replaced = new ArrayList<>();
        for (String existing : tags) {
            String value = existing.equals(tag) ? name : existing;
            if (!replaced.contains(value)) {
                replaced.add(value);
            }
        }
        return replaced;
    }

    private static List<String> append(List<String> tags, String tag) {
        List<String> appended = new ArrayList<>(tags);
        appended.add(tag);
        return appended;
    }

    private static List<TaggedItems.Item> items(Map<Long, String> titles) {
        return titles.entrySet().stream().map(entry -> new TaggedItems.Item(entry.getKey(), entry.getValue())).toList();
    }

    private static final class Tally {

        private final int total;
        private final List<String> failures = new ArrayList<>();
        private int changed;
        private int unchanged;
        private int failed;

        Tally(int total) {
            this.total = total;
        }

        void change(JobContext context, String label, LongPredicate change, long id) {
            context.throwIfCancellationRequested();
            context.reportProgress(changed + unchanged + failed, total, label);
            try {
                if (change.test(id)) {
                    changed++;
                } else {
                    unchanged++;
                }
            } catch (ErrorResponseException e) {
                failed++;
                if (failures.size() < MAX_LISTED_FAILURES) {
                    failures.add("%s: %s".formatted(label, e.getBody().getDetail()));
                }
            }
        }

        TagChangeResult result() {
            return new TagChangeResult(changed, unchanged, failed, failures);
        }
    }
}
