import { translate } from '@jsverse/transloco';

import { translatedLabels } from '../shared/ui-locale';

/** Comparisons of a filter condition; must match FilterOperator in the backend. */
export type FilterOperator =
  'IS' | 'IS_NOT' | 'CONTAINS' | 'AT_LEAST' | 'AT_MOST' | 'EMPTY' | 'NOT_EMPTY';

export interface FilterCondition<F extends string = string> {
  readonly field: F;
  readonly operator: FilterOperator;
  readonly value: string | null;
}

/** Filter of a list (movies or TV shows); content of the query and definition of a saved filter. */
export interface ListFilter<F extends string = string> {
  readonly search?: string | null;
  readonly matchAll: boolean;
  readonly conditions: readonly FilterCondition<F>[];
}

/** Existing values per selection list, e.g. `genres`; resolutions as numbers. */
export type FilterValues = object;

/** The existing values of a selection list, empty if they are (still) missing. */
export function valuesOf(values: FilterValues | null, list: string): readonly (string | number)[] {
  return (values as Readonly<Record<string, readonly (string | number)[]>> | null)?.[list] ?? [];
}

/** A choice for the comparison value. */
export interface FilterOption {
  readonly value: string;
  readonly label: string;
}

/**
 * Where the choice for the value comes from: the name of a list of existing values (e.g. `genres`,
 * `resolutions`), yes/no, sources or free input (text or number).
 */
export type ValueSource = string;
export const BOOLEAN_VALUES = 'boolean';
export const SOURCE_VALUES = 'sources';
export const TEXT_VALUES = 'text';
export const NUMBER_VALUES = 'number';
export const NO_VALUES = 'none';
export const RESOLUTION_VALUES = 'resolutions';

/** A field of the filter; `label` is translated whenever it is read. */
export interface FieldDefinition {
  readonly label: string;
  readonly operators: readonly FilterOperator[];
  readonly values: ValueSource;
}

/** Defines a field whose label follows the active UI language. */
export function fieldDefinition(
  labelKey: string,
  operators: readonly FilterOperator[],
  values: ValueSource,
): FieldDefinition {
  return {
    get label() {
      return translate(labelKey);
    },
    operators,
    values,
  };
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

/** Names of the comparisons in the active UI language. */
export const OPERATOR_LABELS: Readonly<Record<FilterOperator, string>> = translatedLabels({
  IS: 'filters.operators.is',
  IS_NOT: 'filters.operators.isNot',
  CONTAINS: 'filters.operators.contains',
  AT_LEAST: 'filters.operators.atLeast',
  AT_MOST: 'filters.operators.atMost',
  EMPTY: 'filters.operators.empty',
  NOT_EMPTY: 'filters.operators.notEmpty',
});

export function needsValue(operator: FilterOperator): boolean {
  return operator !== 'EMPTY' && operator !== 'NOT_EMPTY';
}

/** Resolution level as in Kodi, e.g. 2160 → “4K”. */
export function resolutionLabel(lines: number): string {
  return lines === 2160 ? '4K' : lines === 4320 ? '8K' : `${lines}p`;
}

/** A filter that comes with the application; `name` is translated whenever it is read. */
export interface PredefinedFilter<F extends string = string> {
  readonly name: string;
  readonly filter: ListFilter<F>;
}

/** Defines a predefined filter whose name follows the active UI language. */
export function predefinedFilter<F extends string>(
  nameKey: string,
  filter: ListFilter<F>,
): PredefinedFilter<F> {
  return {
    get name() {
      return translate(nameKey);
    },
    filter,
  };
}

export function only<F extends string>(condition: FilterCondition<F>): ListFilter<F> {
  return { matchAll: true, conditions: [condition] };
}

/** Conditions that lack no value; only these go to the backend. */
export function completeConditions<F extends string>(filter: ListFilter<F>): ListFilter<F> {
  return {
    ...filter,
    conditions: filter.conditions.filter(
      (condition) => !needsValue(condition.operator) || condition.value !== null,
    ),
  };
}
