package de.tiktak.kmm.filter;

/**
 * Ein Feld, nach dem eine Liste gefiltert werden kann, mit seinen erlaubten Vergleichen.
 */
public interface FilterField {

    String name();

    ValueType valueType();

    boolean allows(FilterOperator operator);

    /** Art des Vergleichswerts; {@link #NONE} bei Vergleichen ohne Wert. */
    enum ValueType {
        TEXT, NUMBER, BOOLEAN, NONE
    }
}
