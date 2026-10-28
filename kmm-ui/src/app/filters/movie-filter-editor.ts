import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { MediaSource } from '../sources/media-source';
import {
  FilterCondition,
  FilterOperator,
  FilterOption,
  MOVIE_FILTER_FIELDS,
  MovieFilter,
  MovieFilterField,
  MovieFilterValues,
  OPERATOR_LABELS,
  needsValue,
  resolutionLabel,
} from './movie-filter';

const BOOLEAN_OPTIONS: readonly FilterOption[] = [
  { value: 'true', label: 'ja' },
  { value: 'false', label: 'nein' },
];

/**
 * Bedingungen eines Filmfilters bearbeiten: Feld, Vergleich und Wert je Zeile, dazu „alle“ oder „eine“. Jede
 * Änderung wird sofort gemeldet; unvollständige Zeilen filtert die Liste nicht.
 */
@Component({
  selector: 'app-movie-filter-editor',
  imports: [MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  templateUrl: './movie-filter-editor.html',
  styleUrl: './movie-filter-editor.scss',
})
export class MovieFilterEditor {
  readonly filter = input.required<MovieFilter>();
  readonly values = input<MovieFilterValues | null>(null);
  readonly sources = input<readonly MediaSource[]>([]);
  readonly filterChange = output<MovieFilter>();

  protected readonly fields = Object.entries(MOVIE_FILTER_FIELDS) as [
    MovieFilterField,
    { label: string },
  ][];
  protected readonly operatorLabels = OPERATOR_LABELS;
  protected readonly needsValue = needsValue;

  protected operators(condition: FilterCondition): readonly FilterOperator[] {
    return MOVIE_FILTER_FIELDS[condition.field].operators;
  }

  /** Auswahl für den Wert oder `null` bei freier Eingabe bzw. ohne Wert. */
  protected options(condition: FilterCondition): readonly FilterOption[] | null {
    const source = MOVIE_FILTER_FIELDS[condition.field].values;
    switch (source) {
      case 'text':
      case 'number':
      case 'none':
        return null;
      case 'boolean':
        return BOOLEAN_OPTIONS;
      case 'sources':
        return this.sources()
          .filter((sourceItem) => sourceItem.mediaType === 'MOVIE')
          .map((sourceItem) => ({ value: String(sourceItem.id), label: sourceItem.name }));
      case 'resolutions':
        return (this.values()?.resolutions ?? []).map((lines) => ({
          value: String(lines),
          label: resolutionLabel(lines),
        }));
      default:
        return withCurrent(
          (this.values()?.[source] ?? []).map((value) => ({ value, label: value })),
          condition.value,
        );
    }
  }

  protected isNumber(condition: FilterCondition): boolean {
    return MOVIE_FILTER_FIELDS[condition.field].values === 'number';
  }

  protected setMatchAll(matchAll: boolean): void {
    this.emit({ ...this.filter(), matchAll });
  }

  protected add(): void {
    this.emit({
      ...this.filter(),
      conditions: [...this.filter().conditions, { field: 'GENRE', operator: 'IS', value: null }],
    });
  }

  protected remove(index: number): void {
    this.emit({
      ...this.filter(),
      conditions: this.filter().conditions.filter((_, i) => i !== index),
    });
  }

  protected setField(index: number, field: MovieFilterField): void {
    const definition = MOVIE_FILTER_FIELDS[field];
    const value = definition.values === 'boolean' ? 'false' : null;
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

  private replace(index: number, condition: FilterCondition): void {
    this.emit({
      ...this.filter(),
      conditions: this.filter().conditions.map((current, i) => (i === index ? condition : current)),
    });
  }

  private emit(filter: MovieFilter): void {
    this.filterChange.emit(filter);
  }
}

/** Ein gespeicherter Wert bleibt wählbar, auch wenn ihn gerade kein Film mehr hat. */
function withCurrent(options: FilterOption[], current: string | null): readonly FilterOption[] {
  if (
    current === null ||
    options.some((option) => option.value.toLowerCase() === current.toLowerCase())
  ) {
    return options;
  }
  return [{ value: current, label: current }, ...options];
}

/** Bedingungen, denen kein Wert fehlt; nur sie gehen an das Backend. */
export function completeConditions(filter: MovieFilter): MovieFilter {
  return {
    ...filter,
    conditions: filter.conditions.filter(
      (condition) => !needsValue(condition.operator) || condition.value !== null,
    ),
  };
}
