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

import de.tiktak.kmm.i18n.Localized;
import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.i18n.Messages;
import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.movie.MovieTagService;
import de.tiktak.kmm.tvshow.TvShowTagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tag manager as in Ember: count, rename, delete, assign and remove tags of movies and TV shows. Tags
 * exist only on movies and TV shows; a tag without entries disappears. Changes write the NFOs of the
 * affected entries and therefore run as a background job.
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

    /** @return all tags with the number of their movies and TV shows, sorted by name */
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

    /** @return the movies and TV shows with the tag */
    public TaggedItems findTagged(String tag) {
        return new TaggedItems(tag, items(movieTags.findTitlesByTag(tag)), items(showTags.findTitlesByTag(tag)));
    }

    /** Renames a tag on all movies and TV shows; if an entry carries both names, one remains. */
    public JobSnapshot rename(String tag, String newName) {
        String name = TagNames.clean(newName);
        return submit(LocalizedText.of("tag.rename.jobTitle").with("tag", tag).with("name", name),
                movieTags.findIdsByTag(tag),
                showTags.findIdsByTag(tag), tags -> replace(tags, tag, name));
    }

    /** Removes a tag from all movies and TV shows. */
    public JobSnapshot delete(String tag) {
        return submit(LocalizedText.of("tag.delete.jobTitle").with("tag", tag), movieTags.findIdsByTag(tag),
                showTags.findIdsByTag(tag),
                tags -> tags.stream().filter(existing -> !existing.equals(tag)).toList());
    }

    /** Adds a tag to the selected movies and TV shows, including a new one. */
    public JobSnapshot assign(TagAssignment assignment) {
        String tag = TagNames.clean(assignment.tag());
        return submit(LocalizedText.of("tag.assign.jobTitle").with("tag", tag), assignment.movieIds(),
                assignment.showIds(),
                tags -> tags.contains(tag) ? tags : append(tags, tag));
    }

    /** Removes a tag from the selected movies and TV shows. */
    public JobSnapshot unassign(TagAssignment assignment) {
        String tag = assignment.tag();
        return submit(LocalizedText.of("tag.unassign.jobTitle").with("tag", tag), assignment.movieIds(),
                assignment.showIds(),
                tags -> tags.stream().filter(existing -> !existing.equals(tag)).toList());
    }

    private JobSnapshot submit(LocalizedText title, Collection<Long> movieIds, Collection<Long> showIds,
                               UnaryOperator<List<String>> change) {
        List<Long> movies = List.copyOf(new LinkedHashSet<>(movieIds));
        List<Long> shows = List.copyOf(new LinkedHashSet<>(showIds));
        return jobService.submit(JOB_KEY, title, context -> apply(title, movies, shows, change, context));
    }

    TagChangeResult apply(LocalizedText title, List<Long> movieIds, List<Long> showIds,
                          UnaryOperator<List<String>> change, JobContext context) {
        Tally tally = new Tally(movieIds.size() + showIds.size());
        for (long movieId : movieIds) {
            tally.change(context, LocalizedText.of("tag.item.movie").with("id", movieId),
                    id -> movieTags.changeTags(id, change), movieId);
        }
        for (long showId : showIds) {
            tally.change(context, LocalizedText.of("tag.item.tvShow").with("id", showId),
                    id -> showTags.changeTags(id, change), showId);
        }
        TagChangeResult result = tally.result();
        log.info("{}: {} changed, {} unchanged, {} failed", Messages.english(title), result.changed(),
                result.unchanged(), result.failed());
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
        private final List<LocalizedText> failures = new ArrayList<>();
        private int changed;
        private int unchanged;
        private int failed;

        Tally(int total) {
            this.total = total;
        }

        void change(JobContext context, LocalizedText label, LongPredicate change, long id) {
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
                    failures.add(LocalizedText.of("tag.failure").with("entry", label).with("reason", reason(e)));
                }
            }
        }

        private static LocalizedText reason(ErrorResponseException e) {
            return e instanceof Localized localized ? localized.localizedText()
                    : LocalizedText.literal(e.getBody().getDetail());
        }

        TagChangeResult result() {
            return new TagChangeResult(changed, unchanged, failed, failures);
        }
    }
}
