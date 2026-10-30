import { Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { MediaSource, MediaType } from '../sources/media-source';
import {
  BOOLEAN_VALUES,
  FieldDefinition,
  FilterCondition,
  FilterOperator,
  FilterOption,
  FilterValues,
  ListFilter,
  NO_VALUES,
  NUMBER_VALUES,
  OPERATOR_LABELS,
  RESOLUTION_VALUES,
  SOURCE_VALUES,
  TEXT_VALUES,
  needsValue,
  resolutionLabel,
  valuesOf,
} from './filter';
import { HelpLink } from '../shared/help-link';

const BOOLEAN_OPTIONS: readonly FilterOption[] = [
  { value: 'true', label: 'ja' },
  { value: 'false', label: 'nein' },
];
const NEW_CONDITION_FIELD = 'GENRE';

/**
 * Bedingungen eines Filters bearbeiten: Feld, Vergleich und Wert je Zeile, dazu „alle“ oder „eine“. Welche Felder
 * es gibt, bestimmt die Liste (Filme oder Serien). Jede Änderung wird sofort gemeldet; unvollständige Zeilen filtert
 * die Liste nicht.
 */
@Component({
  selector: 'app-filter-editor',
  imports: [HelpLink, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  templateUrl: './filter-editor.html',
  styleUrl: './filter-editor.scss',
})
export class FilterEditor {
  readonly fields = input.required<Readonly<Record<string, FieldDefinition>>>();
  readonly filter = input.required<ListFilter>();
  readonly values = input<FilterValues | null>(null);
  readonly sources = input<readonly MediaSource[]>([]);
  /** Welche Quellen für das Feld „Quelle“ angeboten werden. */
  readonly sourceMediaType = input<MediaType>('MOVIE');
  readonly filterChange = output<ListFilter>();

  protected readonly fieldEntries = computed(() => Object.entries(this.fields()));
  protected readonly operatorLabels = OPERATOR_LABELS;
  protected readonly needsValue = needsValue;

  protected operators(condition: FilterCondition): readonly FilterOperator[] {
    return this.definition(condition.field).operators;
  }

  /** Auswahl für den Wert oder `null` bei freier Eingabe bzw. ohne Wert. */
  protected options(condition: FilterCondition): readonly FilterOption[] | null {
    const source = this.definition(condition.field).values;
    switch (source) {
      case TEXT_VALUES:
      case NUMBER_VALUES:
      case NO_VALUES:
        return null;
      case BOOLEAN_VALUES:
        return BOOLEAN_OPTIONS;
      case SOURCE_VALUES:
        return this.sources()
          .filter((sourceItem) => sourceItem.mediaType === this.sourceMediaType())
          .map((sourceItem) => ({ value: String(sourceItem.id), label: sourceItem.name }));
      case RESOLUTION_VALUES:
        return valuesOf(this.values(), RESOLUTION_VALUES).map((lines) => ({
          value: String(lines),
          label: resolutionLabel(Number(lines)),
        }));
      default:
        return withCurrent(
          valuesOf(this.values(), source).map((value) => ({
            value: String(value),
            label: String(value),
          })),
          condition.value,
        );
    }
  }

  protected isNumber(condition: FilterCondition): boolean {
    return this.definition(condition.field).values === NUMBER_VALUES;
  }

  protected setMatchAll(matchAll: boolean): void {
    this.emit({ ...this.filter(), matchAll });
  }

  protected add(): void {
    this.emit({
      ...this.filter(),
      conditions: [
        ...this.filter().conditions,
        { field: NEW_CONDITION_FIELD, operator: 'IS', value: null },
      ],
    });
  }

  protected remove(index: number): void {
    this.emit({
      ...this.filter(),
      conditions: this.filter().conditions.filter((_, i) => i !== index),
    });
  }

  protected setField(index: number, field: string): void {
    const definition = this.definition(field);
    const value = definition.values === BOOLEAN_VALUES ? 'false' : null;
    this.replace(index, { field, operator: definition.operators[0], value });
  }

  protected setOperator(index: number, operator: FilterOperator): void {
    const condition = this.filter().conditions[index];
    this.replace(index, {
      ...condition,
      operator,
      value: needsValue(operator) ? condition.value : null,
    });
  }

  protected setValue(index: number, value: string): void {
    this.replace(index, { ...this.filter().conditions[index], value: value.trim() || null });
  }

  private definition(field: string): FieldDefinition {
    return this.fields()[field];
  }

  private replace(index: number, condition: FilterCondition): void {
    this.emit({
      ...this.filter(),
      conditions: this.filter().conditions.map((current, i) => (i === index ? condition : current)),
    });
  }

  private emit(filter: ListFilter): void {
    this.filterChange.emit(filter);
  }
}

/** Ein gespeicherter Wert bleibt wählbar, auch wenn ihn gerade kein Eintrag mehr hat. */
function withCurrent(options: FilterOption[], current: string | null): readonly FilterOption[] {
  if (
    current === null ||
    options.some((option) => option.value.toLowerCase() === current.toLowerCase())
  ) {
    return options;
  }
  return [{ value: current, label: current }, ...options];
}
