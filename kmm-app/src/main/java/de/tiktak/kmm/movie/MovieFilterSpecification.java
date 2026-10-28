package de.tiktak.kmm.movie;

import java.time.Clock;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import de.tiktak.kmm.filter.FilterPredicates;
import de.tiktak.kmm.filter.InvalidFilterException;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Übersetzt einen {@link MovieFilter} in eine Datenbankabfrage.
 */
final class MovieFilterSpecification {

    private MovieFilterSpecification() {
    }

    /**
     * @throws InvalidFilterException wenn eine Bedingung den Vergleich nicht erlaubt oder der Wert fehlt oder
     *                                nicht passt
     */
    static Specification<Movie> of(MovieFilter filter, Clock clock) {
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
    private record Builder(Root<Movie> root, CriteriaQuery<?> query, FilterPredicates predicates) {

        Predicate predicate(MovieFilter.Condition condition) {
            return switch (condition.field()) {
                case TITLE -> text(root.get("title"), condition);
                case YEAR -> number(root.get("releaseYear"), condition);
                case GENRE -> collection("genres", condition);
                case TAG -> collection("tags", condition);
                case COUNTRY -> collection("countries", condition);
                case STUDIO -> collection("studios", condition);
                case SET -> text(root.join("movieSet", JoinType.LEFT).get("name"), condition);
                case WATCHED -> watched(condition);
                case USER_RATING -> number(root.get("userRating"), condition);
                case MPAA -> text(root.get("mpaa"), condition);
                case EDITION -> text(root.get("edition"), condition);
                case VIDEO_SOURCE -> text(root.get("videoSource"), condition);
                case RESOLUTION -> number(streamIndex().get("videoResolution"), condition);
                case VIDEO_CODEC -> text(streamIndex().get("videoCodec"), condition);
                case HDR -> text(streamIndex().get("hdrType"), condition);
                case AUDIO_CODEC -> token(streamIndex().get("audioCodecs"), condition);
                case AUDIO_LANGUAGE -> token(streamIndex().get("audioLanguages"), condition);
                case SUBTITLE_LANGUAGE -> token(streamIndex().get("subtitleLanguages"), condition);
                case STREAM_DETAILS -> predicates.presence(streamIndex().get("audioCodecs"), condition.operator());
                case TMDB_ID -> predicates.presence(root.get("tmdbId"), condition.operator());
                case IMDB_ID -> predicates.presence(root.get("imdbId"), condition.operator());
                case NFO -> predicates.presence(root.get("nfoModifiedAt"), condition.operator());
                case TRAILER -> predicates.presence(root.get("trailer"), condition.operator());
                case SOURCE -> number(root.get("sourceId"), condition);
                case ADDED_DAYS_AGO -> predicates.addedDaysAgo(root.get("addedAt"), condition.field(),
                        condition.operator(), condition.value());
            };
        }

        private Path<Object> streamIndex() {
            return root.get("streamIndex");
        }

        private Predicate text(Path<String> path, MovieFilter.Condition condition) {
            return predicates.text(path, condition.field(), condition.operator(), condition.value());
        }

        private Predicate number(Path<Integer> path, MovieFilter.Condition condition) {
            return predicates.number(path, condition.field(), condition.operator(), condition.value());
        }

        private Predicate token(Path<String> path, MovieFilter.Condition condition) {
            return predicates.token(path, condition.operator(), condition.value());
        }

        /** Listen wie Genres: „ist“ heißt, ein Eintrag passt; „ist nicht“, keiner passt. */
        private Predicate collection(String attribute, MovieFilter.Condition condition) {
            var cb = predicates.cb();
            return switch (condition.operator()) {
                case IS -> cb.exists(containing(attribute, condition));
                case IS_NOT -> cb.not(cb.exists(containing(attribute, condition)));
                case EMPTY -> cb.isEmpty(root.get(attribute));
                case NOT_EMPTY -> cb.isNotEmpty(root.get(attribute));
                default -> throw InvalidFilterException.operatorNotAllowed(condition.field(), condition.operator());
            };
        }

        private Subquery<Integer> containing(String attribute, MovieFilter.Condition condition) {
            var cb = predicates.cb();
            Subquery<Integer> subquery = query.subquery(Integer.class);
            Root<Movie> movie = subquery.correlate(root);
            Join<Movie, String> entry = movie.join(attribute);
            return subquery.select(cb.literal(1))
                    .where(cb.equal(cb.lower(entry), predicates.lower(condition.value())));
        }

        private Predicate watched(MovieFilter.Condition condition) {
            var cb = predicates.cb();
            Path<Integer> playCount = root.get("playCount");
            Predicate watched = cb.greaterThan(playCount, 0);
            return FilterPredicates.bool(condition.field(), condition.value()) ? watched
                    : cb.or(cb.isNull(playCount), cb.not(watched));
        }
    }
}
