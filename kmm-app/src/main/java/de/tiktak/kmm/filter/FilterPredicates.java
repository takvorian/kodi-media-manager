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
 * Bausteine für Filterabfragen, gemeinsam für Film- und Serienlisten. Texte werden ohne Rücksicht auf Groß- und
 * Kleinschreibung verglichen; „ist nicht“ trifft auch Einträge ohne Wert.
 *
 * @param cb    der Criteria-Builder der Abfrage
 * @param clock Uhr für Bedingungen wie „hinzugefügt vor Tagen“
 */
public record FilterPredicates(CriteriaBuilder cb, Clock clock) {

    private static final String WILDCARD = "%";
    private static final char ESCAPE = StreamIndex.LIKE_ESCAPE;

    /**
     * Prüft Vergleich und Wert, bevor die Abfrage gebaut wird.
     *
     * @throws InvalidFilterException wenn das Feld den Vergleich nicht erlaubt oder der Wert fehlt oder nicht passt
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
                // Text passt immer.
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

    /** Verknüpft die Bedingungen; ohne Bedingung gilt alles. */
    public Predicate combine(List<Predicate> conditions, boolean matchAll) {
        if (conditions.isEmpty()) {
            return cb.conjunction();
        }
        Predicate[] all = conditions.toArray(Predicate[]::new);
        return matchAll ? cb.and(all) : cb.or(all);
    }

    /** Titelsuche zusätzlich zu den Bedingungen. */
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

    /** Ein Eintrag einer Liste in {@link StreamIndex}-Form, z. B. ein Audio-Codec. */
    public Predicate token(Path<String> path, FilterOperator operator, String value) {
        Predicate contained = cb.like(path, StreamIndex.containsToken(value), ESCAPE);
        return operator == FilterOperator.IS ? contained : cb.or(cb.isNull(path), cb.not(contained));
    }

    /** „höchstens n Tage“ heißt: in den letzten n Tagen hinzugefügt. */
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
