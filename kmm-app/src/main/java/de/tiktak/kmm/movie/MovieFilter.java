package de.tiktak.kmm.movie;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import de.tiktak.kmm.filter.FilterOperator;

/**
 * Filter of the movie list made of individual conditions.
 *
 * @param search     part of the title in addition to the conditions, or {@code null}
 * @param matchAll   whether all conditions must apply ({@code true}) or one is enough
 * @param conditions conditions; without a condition, all movies apply
 */
public record MovieFilter(
        @Size(max = MovieFilter.MAX_VALUE_LENGTH) String search,
        boolean matchAll,
        @NotNull @Size(max = MAX_CONDITIONS) List<@Valid @NotNull Condition> conditions) {

    static final int MAX_CONDITIONS = 30;
    static final int MAX_VALUE_LENGTH = 255;

    public static final MovieFilter ALL = new MovieFilter(null, true, List.of());

    public MovieFilter {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        search = search == null || search.isBlank() ? null : search.strip();
    }

    /**
     * @param value comparison value; irrelevant for {@link FilterOperator#EMPTY} and {@link FilterOperator#NOT_EMPTY}
     */
    public record Condition(
            @NotNull MovieFilterField field,
            @NotNull FilterOperator operator,
            @Size(max = MAX_VALUE_LENGTH) String value) {

        public Condition {
            value = value == null || value.isBlank() ? null : value.strip();
        }
    }
}
