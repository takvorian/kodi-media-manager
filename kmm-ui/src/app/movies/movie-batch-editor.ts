import { Component, inject, input, output, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { FieldChange, MovieBatchEdit } from './movie-batch';

type FieldMode = 'KEEP' | 'SET' | 'CLEAR';

const LIST_SEPARATOR = ',';

/**
 * Formular der Stapel-Bearbeitung: Genres und Tags hinzufügen oder entfernen, Altersfreigabe, Fassung und
 * Videoquelle setzen oder leeren. Gemeldet wird die Änderung; ausführen muss sie die Liste.
 */
@Component({
  selector: 'app-movie-batch-editor',
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    ReactiveFormsModule,
  ],
  templateUrl: './movie-batch-editor.html',
  styleUrl: './movie-batch-editor.scss',
})
export class MovieBatchEditor {
  readonly movieIds = input.required<readonly number[]>();
  readonly busy = input(false);
  readonly submitted = output<MovieBatchEdit>();

  protected readonly modes: readonly [FieldMode, string][] = [
    ['KEEP', 'unverändert'],
    ['SET', 'setzen'],
    ['CLEAR', 'leeren'],
  ];
  protected readonly singleFields: readonly ['mpaa' | 'edition' | 'videoSource', string][] = [
    ['mpaa', 'Altersfreigabe'],
    ['edition', 'Fassung'],
    ['videoSource', 'Videoquelle'],
  ];
  protected readonly empty = signal(false);
  protected readonly form = inject(NonNullableFormBuilder).group({
    addGenres: [''],
    removeGenres: [''],
    addTags: [''],
    removeTags: [''],
    mpaaMode: ['KEEP' as FieldMode],
    mpaa: [''],
    editionMode: ['KEEP' as FieldMode],
    edition: [''],
    videoSourceMode: ['KEEP' as FieldMode],
    videoSource: [''],
  });

  protected submit(): void {
    const value = this.form.getRawValue();
    const edit: MovieBatchEdit = {
      movieIds: this.movieIds(),
      genres: { add: split(value.addGenres), remove: split(value.removeGenres) },
      tags: { add: split(value.addTags), remove: split(value.removeTags) },
      mpaa: field(value.mpaaMode, value.mpaa),
      edition: field(value.editionMode, value.edition),
      videoSource: field(value.videoSourceMode, value.videoSource),
    };
    const listChanges = [edit.genres, edit.tags].some(
      (change) => change.add.length > 0 || change.remove.length > 0,
    );
    const fieldChanges = [edit.mpaa, edit.edition, edit.videoSource].some(
      (change) => change.clear || change.value !== null,
    );
    const changesSomething = listChanges || fieldChanges;
    this.empty.set(!changesSomething);
    if (changesSomething) {
      this.submitted.emit(edit);
    }
  }

  protected mode(field: 'mpaa' | 'edition' | 'videoSource'): FieldMode {
    return this.form.controls[`${field}Mode`].value;
  }
}

function split(value: string): string[] {
  return value
    .split(LIST_SEPARATOR)
    .map((entry) => entry.trim())
    .filter((entry) => entry.length > 0);
}

function field(mode: FieldMode, value: string): FieldChange {
  return {
    clear: mode === 'CLEAR',
    value: mode === 'SET' && value.trim() ? value.trim() : null,
  };
}
