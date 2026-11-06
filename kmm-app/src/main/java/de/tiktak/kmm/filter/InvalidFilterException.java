package de.tiktak.kmm.filter;

import org.springframework.http.HttpStatus;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.web.LocalizedProblemException;

/**
 * A filter condition does not fit its field. Answered as HTTP 400.
 */
public class InvalidFilterException extends LocalizedProblemException {

    private InvalidFilterException(LocalizedText detail) {
        super(HttpStatus.BAD_REQUEST, LocalizedText.of("filter.invalid.title"), detail, null);
    }

    public static InvalidFilterException operatorNotAllowed(FilterField field, FilterOperator operator) {
        return new InvalidFilterException(LocalizedText.of("filter.invalid.operatorNotAllowed")
                .with("field", field.name())
                .with("operator", operator));
    }

    static InvalidFilterException valueMissing(FilterField field) {
        return new InvalidFilterException(LocalizedText.of("filter.invalid.valueMissing").with("field", field.name()));
    }

    static InvalidFilterException notANumber(FilterField field, String value) {
        return new InvalidFilterException(LocalizedText.of("filter.invalid.notANumber")
                .with("field", field.name())
                .with("value", value));
    }

    static InvalidFilterException notABoolean(FilterField field, String value) {
        return new InvalidFilterException(LocalizedText.of("filter.invalid.notABoolean")
                .with("field", field.name())
                .with("value", value));
    }
}
