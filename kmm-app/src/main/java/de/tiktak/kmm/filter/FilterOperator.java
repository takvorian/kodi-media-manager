package de.tiktak.kmm.filter;

/**
 * Vergleich einer Filterbedingung. {@link #EMPTY} und {@link #NOT_EMPTY} brauchen keinen Wert.
 */
public enum FilterOperator {
    IS, IS_NOT, CONTAINS, AT_LEAST, AT_MOST, EMPTY, NOT_EMPTY;

    public boolean needsValue() {
        return this != EMPTY && this != NOT_EMPTY;
    }
}
