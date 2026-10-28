package de.tiktak.kmm.movie;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import de.tiktak.kmm.filter.FilterOperator;
import de.tiktak.kmm.persistence.StreamIndex;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Übersetzt einen {@link MovieFilter} in eine Datenbankabfrage. Texte werden ohne Rücksicht auf Groß- und
 * Kleinschreibung verglichen; „ist nicht“ trifft auch Filme ohne Wert.
 */
final class MovieFilterSpecification {

    private static final String WILDCARD = "%";
    private static final char ESCAPE = StreamIndex.LIKE_ESCAPE;

    private MovieFilterSpecification() {
    }

    /**
     * @throws InvalidFilterException wenn eine Bedingung den Vergleich nicht erlaubt oder der Wert fehlt oder
     *                                nicht passt
     */
    static Specification<Movie> of(MovieFilter filter, Clock clock) {
        filter.conditions().forEach(MovieFilterSpecification::validate);
        return (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            for (MovieFilter.Condition condition : filter.conditions()) {
                conditions.add(new Builder(root, query, cb, clock).predicate(condition));
            }
            Predicate combined = conditions.isEmpty() ? cb.conjunction()
                    : filter.matchAll() ? cb.and(conditions.toArray(Predicate[]::new))
                            : cb.or(conditions.toArray(Predicate[]::new));
            if (filter.search() == null) {
                return combined;
            }
            return cb.and(combined, cb.like(cb.lower(root.get("title")), contains(filter.search()), ESCAPE));
        };
    }

    private static void validate(MovieFilter.Condition condition) {
        MovieFilterField field = condition.field();
        if (!field.allows(condition.operator())) {
            throw InvalidFilterException.operatorNotAllowed(field, condition.operator());
        }
        if (!condition.operator().needsValue()) {
            return;
        }
        if (condition.value() == null) {
            throw InvalidFilterException.valueMissing(field);
        }
        switch (field.valueType()) {
            case NUMBER -> number(condition);
            case BOOLEAN -> bool(condition);
            default -> {
                // Text passt immer.
            }
        }
    }

    private static int number(MovieFilter.Condition condition) {
        try {
            return Integer.parseInt(condition.value());
        } catch (NumberFormatException e) {
            throw InvalidFilterException.notANumber(condition.field(), condition.value());
        }
    }

    private static boolean bool(MovieFilter.Condition condition) {
        return switch (condition.value().toLowerCase(Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw InvalidFilterException.notABoolean(condition.field(), condition.value());
        };
    }

    private static String contains(String value) {
        return WILDCARD + StreamIndex.escapeLike(value.toLowerCase(Locale.ROOT)) + WILDCARD;
    }

    /** Baut die Bedingung einer Zeile der Abfrage. */
    private record Builder(Root<Movie> root, CriteriaQuery<?> query, CriteriaBuilder cb, Clock clock) {

        Predicate predicate(MovieFilter.Condition condition) {
            return switch (condition.field()) {
                case TITLE -> text(root.get("title"), condition);
                case YEAR -> number(root.get("releaseYear"), condition);
                case GENRE -> collection("genres", condition);
                case TAG -> collection("tags", condition);
                case COUNTRY -> collection("countries", condition);
                case STUDIO -> collection("studios", condition);
                case SET -> set(condition);
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
                case STREAM_DETAILS -> presence(streamIndex().get("audioCodecs"), condition);
                case TMDB_ID -> presence(root.get("tmdbId"), condition);
                case IMDB_ID -> presence(root.get("imdbId"), condition);
                case NFO -> presence(root.get("nfoModifiedAt"), condition);
                case SOURCE -> number(root.get("sourceId"), condition);
                case ADDED_DAYS_AGO -> addedDaysAgo(condition);
            };
        }

        private Path<Object> streamIndex() {
            return root.get("streamIndex");
        }

        private Predicate text(Path<String> path, MovieFilter.Condition condition) {
            Expression<String> lower = cb.lower(path);
            return switch (condition.operator()) {
                case CONTAINS -> cb.like(lower, contains(condition.value()), ESCAPE);
                case IS -> cb.equal(lower, lowerValue(condition));
                case IS_NOT -> cb.or(cb.isNull(path), cb.notEqual(lower, lowerValue(condition)));
                case EMPTY -> cb.or(cb.isNull(path), cb.equal(path, ""));
                case NOT_EMPTY -> cb.and(cb.isNotNull(path), cb.notEqual(path, ""));
                default -> throw InvalidFilterException.operatorNotAllowed(condition.field(), condition.operator());
            };
        }

        private Predicate number(Path<Integer> path, MovieFilter.Condition condition) {
            return switch (condition.operator()) {
                case IS -> cb.equal(path, MovieFilterSpecification.number(condition));
                case IS_NOT -> cb.or(cb.isNull(path), cb.notEqual(path, MovieFilterSpecification.number(condition)));
                case AT_LEAST -> cb.greaterThanOrEqualTo(path, MovieFilterSpecification.number(condition));
                case AT_MOST -> cb.lessThanOrEqualTo(path, MovieFilterSpecification.number(condition));
                case EMPTY -> cb.isNull(path);
                case NOT_EMPTY -> cb.isNotNull(path);
                default -> throw InvalidFilterException.operatorNotAllowed(condition.field(), condition.operator());
            };
        }

        private Predicate presence(Path<?> path, MovieFilter.Condition condition) {
            return condition.operator() == FilterOperator.EMPTY ? cb.isNull(path) : cb.isNotNull(path);
        }

        /** Listen wie Genres: „ist“ heißt, ein Eintrag passt; „ist nicht“, keiner passt. */
        private Predicate collection(String attribute, MovieFilter.Condition condition) {
            return switch (condition.operator()) {
                case IS -> cb.exists(containing(attribute, condition));
                case IS_NOT -> cb.not(cb.exists(containing(attribute, condition)));
                case EMPTY -> cb.isEmpty(root.get(attribute));
                case NOT_EMPTY -> cb.isNotEmpty(root.get(attribute));
                default -> throw InvalidFilterException.operatorNotAllowed(condition.field(), condition.operator());
            };
        }

        private Subquery<Integer> containing(String attribute, MovieFilter.Condition condition) {
            Subquery<Integer> subquery = query.subquery(Integer.class);
            Root<Movie> movie = subquery.correlate(root);
            Join<Movie, String> entry = movie.join(attribute);
            return subquery.select(cb.literal(1)).where(cb.equal(cb.lower(entry), lowerValue(condition)));
        }

        private Predicate set(MovieFilter.Condition condition) {
            Join<Movie, MovieSet> set = root.join("movieSet", JoinType.LEFT);
            return text(set.get("name"), condition);
        }

        private Predicate watched(MovieFilter.Condition condition) {
            Path<Integer> playCount = root.get("playCount");
            Predicate watched = cb.greaterThan(playCount, 0);
            return bool(condition) ? watched : cb.or(cb.isNull(playCount), cb.not(watched));
        }

        private Predicate token(Path<String> path, MovieFilter.Condition condition) {
            Predicate contained = cb.like(path, StreamIndex.containsToken(condition.value()), ESCAPE);
            return condition.operator() == FilterOperator.IS ? contained : cb.or(cb.isNull(path), cb.not(contained));
        }

        private Predicate addedDaysAgo(MovieFilter.Condition condition) {
            Instant boundary = clock.instant().minus(Duration.ofDays(MovieFilterSpecification.number(condition)));
            Path<Instant> addedAt = root.get("addedAt");
            return condition.operator() == FilterOperator.AT_MOST ? cb.greaterThanOrEqualTo(addedAt, boundary)
                    : cb.lessThanOrEqualTo(addedAt, boundary);
        }

        private String lowerValue(MovieFilter.Condition condition) {
            return condition.value().toLowerCase(Locale.ROOT);
        }
    }
}
