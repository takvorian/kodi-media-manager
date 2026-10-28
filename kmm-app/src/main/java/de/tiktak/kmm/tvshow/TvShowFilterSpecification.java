package de.tiktak.kmm.tvshow;

import java.time.Clock;
import java.util.List;
import java.util.function.Function;

import org.springframework.data.jpa.domain.Specification;

import de.tiktak.kmm.filter.FilterOperator;
import de.tiktak.kmm.filter.FilterPredicates;
import de.tiktak.kmm.filter.InvalidFilterException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Übersetzt einen {@link TvShowFilter} in eine Datenbankabfrage; Bedingungen an Episoden werden als Unterabfragen
 * gestellt.
 */
final class TvShowFilterSpecification {

    private TvShowFilterSpecification() {
    }

    /**
     * @throws InvalidFilterException wenn eine Bedingung den Vergleich nicht erlaubt oder der Wert fehlt oder
     *                                nicht passt
     */
    static Specification<TvShow> of(TvShowFilter filter, Clock clock) {
        filter.conditions().forEach(condition ->
                FilterPredicates.validate(condition.field(), condition.operator(), condition.value()));
        return (root, query, cb) -> {
            Builder builder = new Builder(root, query, new FilterPredicates(cb, clock));
            List<Predicate> conditions = filter.conditions().stream().map(builder::predicate).toList();
            return builder.predicates().withSearch(builder.predicates().combine(conditions, filter.matchAll()),
                    root.get("title"), filter.search());
        };
    }

    /** Baut die Bedingung einer Zeile der Abfrage. */
    private record Builder(Root<TvShow> root, CriteriaQuery<?> query, FilterPredicates predicates) {

        Predicate predicate(TvShowFilter.Condition condition) {
            return switch (condition.field()) {
                case TITLE -> text(root.get("title"), condition);
                case YEAR -> number(root.get("releaseYear"), condition);
                case GENRE -> collection("genres", condition);
                case TAG -> collection("tags", condition);
                case STUDIO -> collection("studios", condition);
                case STATUS -> text(root.get("status"), condition);
                case MPAA -> text(root.get("mpaa"), condition);
                case USER_RATING -> number(root.get("userRating"), condition);
                case TVDB_ID -> predicates.presence(root.get("tvdbId"), condition.operator());
                case TMDB_ID -> predicates.presence(root.get("tmdbId"), condition.operator());
                case IMDB_ID -> predicates.presence(root.get("imdbId"), condition.operator());
                case NFO -> predicates.presence(root.get("nfoModifiedAt"), condition.operator());
                case SOURCE -> number(root.get("sourceId"), condition);
                case ADDED_DAYS_AGO -> predicates.addedDaysAgo(root.get("addedAt"), condition.field(),
                        condition.operator(), condition.value());
                case EPISODES -> episodeCount(condition);
                case UNWATCHED_EPISODES -> unwatchedEpisodes(condition);
                case EPISODE_RESOLUTION -> anyEpisode(episode -> predicates.number(
                        index(episode).get("videoResolution"), condition.field(), condition.operator(),
                        condition.value()));
                case EPISODE_VIDEO_CODEC -> episodeText(condition, "videoCodec", true);
                case EPISODE_VIDEO_SOURCE -> episodeText(condition, "videoSource", false);
                case EPISODE_AUDIO_CODEC -> episodeToken(condition, "audioCodecs");
                case EPISODE_AUDIO_LANGUAGE -> episodeToken(condition, "audioLanguages");
                case EPISODE_SUBTITLE_LANGUAGE -> episodeToken(condition, "subtitleLanguages");
                case EPISODE_STREAM_DETAILS -> streamDetailsOfAllEpisodes(condition);
            };
        }

        private Predicate text(Path<String> path, TvShowFilter.Condition condition) {
            return predicates.text(path, condition.field(), condition.operator(), condition.value());
        }

        private Predicate number(Path<Integer> path, TvShowFilter.Condition condition) {
            return predicates.number(path, condition.field(), condition.operator(), condition.value());
        }

        /** Listen wie Genres: „ist“ heißt, ein Eintrag passt; „ist nicht“, keiner passt. */
        private Predicate collection(String attribute, TvShowFilter.Condition condition) {
            CriteriaBuilder cb = predicates.cb();
            return switch (condition.operator()) {
                case IS -> cb.exists(containing(attribute, condition));
                case IS_NOT -> cb.not(cb.exists(containing(attribute, condition)));
                case EMPTY -> cb.isEmpty(root.get(attribute));
                case NOT_EMPTY -> cb.isNotEmpty(root.get(attribute));
                default -> throw InvalidFilterException.operatorNotAllowed(condition.field(), condition.operator());
            };
        }

        private Subquery<Integer> containing(String attribute, TvShowFilter.Condition condition) {
            CriteriaBuilder cb = predicates.cb();
            Subquery<Integer> subquery = query.subquery(Integer.class);
            Root<TvShow> show = subquery.correlate(root);
            Join<TvShow, String> entry = show.join(attribute);
            return subquery.select(cb.literal(1))
                    .where(cb.equal(cb.lower(entry), predicates.lower(condition.value())));
        }

        /** „ist“: eine Episode hat den Wert; „ist nicht“: keine Episode hat ihn. */
        private Predicate episodeText(TvShowFilter.Condition condition, String attribute, boolean inIndex) {
            Predicate any = anyEpisode(episode -> {
                Path<String> path = inIndex ? index(episode).get(attribute) : episode.get(attribute);
                return predicates.text(path, condition.field(), FilterOperator.IS, condition.value());
            });
            return condition.operator() == FilterOperator.IS ? any : predicates.cb().not(any);
        }

        private Predicate episodeToken(TvShowFilter.Condition condition, String attribute) {
            Predicate any = anyEpisode(episode -> predicates.token(index(episode).get(attribute), FilterOperator.IS,
                    condition.value()));
            return condition.operator() == FilterOperator.IS ? any : predicates.cb().not(any);
        }

        private Predicate unwatchedEpisodes(TvShowFilter.Condition condition) {
            CriteriaBuilder cb = predicates.cb();
            Predicate any = anyEpisode(episode -> {
                Path<Integer> playCount = episode.get("playCount");
                return cb.or(cb.isNull(playCount), cb.lessThanOrEqualTo(playCount, 0));
            });
            return FilterPredicates.bool(condition.field(), condition.value()) ? any : cb.not(any);
        }

        private Predicate streamDetailsOfAllEpisodes(TvShowFilter.Condition condition) {
            Predicate anyWithout = anyEpisode(episode -> predicates.cb().isNull(index(episode).get("audioCodecs")));
            return condition.operator() == FilterOperator.EMPTY ? anyWithout : predicates.cb().not(anyWithout);
        }

        private Predicate episodeCount(TvShowFilter.Condition condition) {
            CriteriaBuilder cb = predicates.cb();
            Subquery<Long> count = query.subquery(Long.class);
            Root<Episode> episode = count.from(Episode.class);
            count.select(cb.count(episode)).where(cb.equal(episode.get("tvShowId"), root.get("id")));
            long value = FilterPredicates.number(condition.field(), condition.value());
            return switch (condition.operator()) {
                case IS -> cb.equal(count, value);
                case AT_LEAST -> cb.greaterThanOrEqualTo(count, value);
                case AT_MOST -> cb.lessThanOrEqualTo(count, value);
                default -> throw InvalidFilterException.operatorNotAllowed(condition.field(), condition.operator());
            };
        }

        private Predicate anyEpisode(Function<Root<Episode>, Predicate> condition) {
            CriteriaBuilder cb = predicates.cb();
            Subquery<Integer> subquery = query.subquery(Integer.class);
            Root<Episode> episode = subquery.from(Episode.class);
            subquery.select(cb.literal(1))
                    .where(cb.equal(episode.get("tvShowId"), root.get("id")), condition.apply(episode));
            return cb.exists(subquery);
        }

        private static Path<Object> index(Root<Episode> episode) {
            return episode.get("streamIndex");
        }
    }
}
