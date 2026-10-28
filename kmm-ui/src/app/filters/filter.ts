/** Vergleiche einer Filterbedingung; muss zu FilterOperator im Backend passen. */
export type FilterOperator =
  'IS' | 'IS_NOT' | 'CONTAINS' | 'AT_LEAST' | 'AT_MOST' | 'EMPTY' | 'NOT_EMPTY';

export interface FilterCondition<F extends string = string> {
  readonly field: F;
  readonly operator: FilterOperator;
  readonly value: string | null;
}

/** Filter einer Liste (Filme oder Serien); Inhalt der Abfrage und Definition eines gespeicherten Filters. */
export interface ListFilter<F extends string = string> {
  readonly search?: string | null;
  readonly matchAll: boolean;
  readonly conditions: readonly FilterCondition<F>[];
}

/** Vorhandene Werte je Auswahlliste, z. B. `genres`; Auflösungen als Zahlen. */
export type FilterValues = object;

/** Die vorhandenen Werte einer Auswahlliste, leer, wenn sie (noch) fehlen. */
export function valuesOf(values: FilterValues | null, list: string): readonly (string | number)[] {
  return (values as Readonly<Record<string, readonly (string | number)[]>> | null)?.[list] ?? [];
}

/** Eine Auswahlmöglichkeit für den Vergleichswert. */
export interface FilterOption {
  readonly value: string;
  readonly label: string;
}

/**
 * Woher die Auswahl für den Wert kommt: der Name einer Liste der vorhandenen Werte (z. B. `genres`, `resolutions`),
 * ja/nein, Quellen oder freie Eingabe (Text bzw. Zahl).
 */
export type ValueSource = string;
export const BOOLEAN_VALUES = 'boolean';
export const SOURCE_VALUES = 'sources';
export const TEXT_VALUES = 'text';
export const NUMBER_VALUES = 'number';
export const NO_VALUES = 'none';
export const RESOLUTION_VALUES = 'resolutions';

export interface FieldDefinition {
  readonly label: string;
  readonly operators: readonly FilterOperator[];
  readonly values: ValueSource;
}

export const LIST_OPERATORS: readonly FilterOperator[] = ['IS', 'IS_NOT', 'EMPTY', 'NOT_EMPTY'];
export const NUMBER_OPERATORS: readonly FilterOperator[] = [
  'IS',
  'AT_LEAST',
  'AT_MOST',
  'EMPTY',
  'NOT_EMPTY',
];
export const PRESENCE_OPERATORS: readonly FilterOperator[] = ['EMPTY', 'NOT_EMPTY'];
export const TOKEN_OPERATORS: readonly FilterOperator[] = ['IS', 'IS_NOT'];

export const OPERATOR_LABELS: Readonly<Record<FilterOperator, string>> = {
  IS: 'ist',
  IS_NOT: 'ist nicht',
  CONTAINS: 'enthält',
  AT_LEAST: 'mindestens',
  AT_MOST: 'höchstens',
  EMPTY: 'fehlt',
  NOT_EMPTY: 'vorhanden',
};

export function needsValue(operator: FilterOperator): boolean {
  return operator !== 'EMPTY' && operator !== 'NOT_EMPTY';
}

/** Auflösungsstufe wie Kodi, z. B. 2160 → „4K“. */
export function resolutionLabel(lines: number): string {
  return lines === 2160 ? '4K' : lines === 4320 ? '8K' : `${lines}p`;
}

/** Ein mitgelieferter Filter. */
export interface PredefinedFilter<F extends string = string> {
  readonly name: string;
  readonly filter: ListFilter<F>;
}

export function only<F extends string>(condition: FilterCondition<F>): ListFilter<F> {
  return { matchAll: true, conditions: [condition] };
}

/** Bedingungen, denen kein Wert fehlt; nur sie gehen an das Backend. */
export function completeConditions<F extends string>(filter: ListFilter<F>): ListFilter<F> {
  return {
    ...filter,
    conditions: filter.conditions.filter(
      (condition) => !needsValue(condition.operator) || condition.value !== null,
    ),
  };
}
