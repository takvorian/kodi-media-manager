package de.tiktak.kmm.filter;

/**
 * Comparison of a filter condition. {@link #EMPTY} and {@link #NOT_EMPTY} need no value.
 */
public enum FilterOperator {
    IS, IS_NOT, CONTAINS, AT_LEAST, AT_MOST, EMPTY, NOT_EMPTY;

    public boolean needsValue() {
        return this != EMPTY && this != NOT_EMPTY;
    }
}
