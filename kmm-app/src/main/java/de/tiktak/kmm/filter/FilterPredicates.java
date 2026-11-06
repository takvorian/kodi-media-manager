package de.tiktak.kmm.filter;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import de.tiktak.kmm.persistence.StreamIndex;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

/**
 * Building blocks for filter queries, shared by movie and TV show lists. Texts are compared
 * case-insensitively; "is not" also matches entries without a value.
 *
 * @param cb    the criteria builder of the query
 * @param clock clock for conditions such as "added days ago"
 */
public record FilterPredicates(CriteriaBuilder cb, Clock clock) {

    private static final String WILDCARD = "%";
    private static final char ESCAPE = StreamIndex.LIKE_ESCAPE;

    /**
     * Checks comparison and value before the query is built.
     *
     * @throws InvalidFilterException if the field does not allow the comparison or the value is missing or does not fit
     */
    public static void validate(FilterField field, FilterOperator operator, String value) {
        if (!field.allows(operator)) {
            throw InvalidFilterException.operatorNotAllowed(field, operator);
        }
        if (!operator.needsValue()) {
            return;
        }
        if (value == null) {
            throw InvalidFilterException.valueMissing(field);
        }
        switch (field.valueType()) {
            case NUMBER -> number(field, value);
            case BOOLEAN -> bool(field, value);
            default -> {
                // Text always fits.
            }
        }
    }

    public static int number(FilterField field, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw InvalidFilterException.notANumber(field, value);
        }
    }

    public static boolean bool(FilterField field, String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw InvalidFilterException.notABoolean(field, value);
        };
    }

    /** Combines the conditions; without a condition everything matches. */
    public Predicate combine(List<Predicate> conditions, boolean matchAll) {
        if (conditions.isEmpty()) {
            return cb.conjunction();
        }
        Predicate[] all = conditions.toArray(Predicate[]::new);
        return matchAll ? cb.and(all) : cb.or(all);
    }

    /** Title search in addition to the conditions. */
    public Predicate withSearch(Predicate conditions, Path<String> title, String search) {
        return search == null ? conditions : cb.and(conditions, cb.like(cb.lower(title), contains(search), ESCAPE));
    }

    public Predicate text(Path<String> path, FilterField field, FilterOperator operator, String value) {
        Expression<String> lower = cb.lower(path);
        return switch (operator) {
            case CONTAINS -> cb.like(lower, contains(value), ESCAPE);
            case IS -> cb.equal(lower, lower(value));
            case IS_NOT -> cb.or(cb.isNull(path), cb.notEqual(lower, lower(value)));
            case EMPTY -> cb.or(cb.isNull(path), cb.equal(path, ""));
            case NOT_EMPTY -> cb.and(cb.isNotNull(path), cb.notEqual(path, ""));
            default -> throw InvalidFilterException.operatorNotAllowed(field, operator);
        };
    }

    public Predicate number(Path<Integer> path, FilterField field, FilterOperator operator, String value) {
        return switch (operator) {
            case IS -> cb.equal(path, number(field, value));
            case IS_NOT -> cb.or(cb.isNull(path), cb.notEqual(path, number(field, value)));
            case AT_LEAST -> cb.greaterThanOrEqualTo(path, number(field, value));
            case AT_MOST -> cb.lessThanOrEqualTo(path, number(field, value));
            case EMPTY -> cb.isNull(path);
            case NOT_EMPTY -> cb.isNotNull(path);
            default -> throw InvalidFilterException.operatorNotAllowed(field, operator);
        };
    }

    public Predicate presence(Path<?> path, FilterOperator operator) {
        return operator == FilterOperator.EMPTY ? cb.isNull(path) : cb.isNotNull(path);
    }

    /** An entry of a list in {@link StreamIndex} form, e.g. an audio codec. */
    public Predicate token(Path<String> path, FilterOperator operator, String value) {
        Predicate contained = cb.like(path, StreamIndex.containsToken(value), ESCAPE);
        return operator == FilterOperator.IS ? contained : cb.or(cb.isNull(path), cb.not(contained));
    }

    /** "at most n days" means: added within the last n days. */
    public Predicate addedDaysAgo(Path<Instant> addedAt, FilterField field, FilterOperator operator, String value) {
        Instant boundary = clock.instant().minus(Duration.ofDays(number(field, value)));
        return operator == FilterOperator.AT_MOST ? cb.greaterThanOrEqualTo(addedAt, boundary)
                : cb.lessThanOrEqualTo(addedAt, boundary);
    }

    public String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static String contains(String value) {
        return WILDCARD + StreamIndex.escapeLike(value.toLowerCase(Locale.ROOT)) + WILDCARD;
    }
}
