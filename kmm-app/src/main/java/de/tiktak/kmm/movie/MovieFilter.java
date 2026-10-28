package de.tiktak.kmm.movie;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import de.tiktak.kmm.filter.FilterOperator;

/**
 * Filter der Filmliste aus einzelnen Bedingungen.
 *
 * @param search     Teil des Titels zusätzlich zu den Bedingungen oder {@code null}
 * @param matchAll   ob alle Bedingungen gelten müssen ({@code true}) oder eine genügt
 * @param conditions Bedingungen; ohne Bedingung gelten alle Filme
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
     * @param value Vergleichswert; bei {@link FilterOperator#EMPTY} und {@link FilterOperator#NOT_EMPTY} ohne Bedeutung
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
