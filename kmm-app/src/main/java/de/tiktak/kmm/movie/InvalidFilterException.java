package de.tiktak.kmm.movie;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.filter.FilterOperator;

/**
 * Eine Filterbedingung passt nicht zu ihrem Feld. Wird als HTTP 400 beantwortet.
 */
public class InvalidFilterException extends ErrorResponseException {

    private InvalidFilterException(String detail) {
        super(HttpStatus.BAD_REQUEST);
        getBody().setTitle("Ungültiger Filter");
        getBody().setDetail(detail);
    }

    static InvalidFilterException operatorNotAllowed(MovieFilterField field, FilterOperator operator) {
        return new InvalidFilterException("%s erlaubt den Vergleich %s nicht.".formatted(field, operator));
    }

    static InvalidFilterException valueMissing(MovieFilterField field) {
        return new InvalidFilterException("Für %s fehlt der Vergleichswert.".formatted(field));
    }

    static InvalidFilterException notANumber(MovieFilterField field, String value) {
        return new InvalidFilterException("%s erwartet eine Zahl, nicht „%s“.".formatted(field, value));
    }

    static InvalidFilterException notABoolean(MovieFilterField field, String value) {
        return new InvalidFilterException("%s erwartet true oder false, nicht „%s“.".formatted(field, value));
    }
}
