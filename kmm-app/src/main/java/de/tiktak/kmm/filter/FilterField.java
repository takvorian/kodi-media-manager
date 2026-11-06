package de.tiktak.kmm.filter;

/**
 * A field a list can be filtered by, with its allowed comparisons.
 */
public interface FilterField {

    String name();

    ValueType valueType();

    boolean allows(FilterOperator operator);

    /** Kind of the comparison value; {@link #NONE} for comparisons without a value. */
    enum ValueType {
        TEXT, NUMBER, BOOLEAN, NONE
    }
}
