package de.tiktak.kmm.filter;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * Eine Filterbedingung passt nicht zu ihrem Feld. Wird als HTTP 400 beantwortet.
 */
public class InvalidFilterException extends ErrorResponseException {

    private InvalidFilterException(String detail) {
        super(HttpStatus.BAD_REQUEST);
        getBody().setTitle("Ungültiger Filter");
        getBody().setDetail(detail);
    }

    public static InvalidFilterException operatorNotAllowed(FilterField field, FilterOperator operator) {
        return new InvalidFilterException("%s erlaubt den Vergleich %s nicht.".formatted(field.name(), operator));
    }

    static InvalidFilterException valueMissing(FilterField field) {
        return new InvalidFilterException("Für %s fehlt der Vergleichswert.".formatted(field.name()));
    }

    static InvalidFilterException notANumber(FilterField field, String value) {
        return new InvalidFilterException("%s erwartet eine Zahl, nicht „%s“.".formatted(field.name(), value));
    }

    static InvalidFilterException notABoolean(FilterField field, String value) {
        return new InvalidFilterException("%s erwartet true oder false, nicht „%s“.".formatted(field.name(), value));
    }
}
