import { FormControl, FormGroup, NonNullableFormBuilder, Validators } from '@angular/forms';

import { MovieMetadata } from './movie';

export const MIN_YEAR = 1870;
export const MAX_YEAR = 2200;
const IMDB_ID_PATTERN = /^tt\d{7,10}$/;
const TMDB_ID_PATTERN = /^\d{1,10}$/;
const GENRE_SEPARATOR = ',';
const MAX_LABEL_LENGTH = 255;

/** Textfelder sind leer statt {@code null}; leere Eingaben gelten beim Speichern als „nicht gesetzt“. */
export type MovieMetadataForm = FormGroup<{
  title: FormControl<string>;
  originalTitle: FormControl<string>;
  sortTitle: FormControl<string>;
  year: FormControl<number | null>;
  premiered: FormControl<string>;
  runtime: FormControl<number | null>;
  mpaa: FormControl<string>;
  edition: FormControl<string>;
  videoSource: FormControl<string>;
  genres: FormControl<string>;
  tagline: FormControl<string>;
  outline: FormControl<string>;
  plot: FormControl<string>;
  imdbId: FormControl<string>;
  tmdbId: FormControl<string>;
}>;

export function createMovieMetadataForm(formBuilder: NonNullableFormBuilder): MovieMetadataForm {
  return formBuilder.group({
    title: ['', [Validators.required, Validators.maxLength(512)]],
    originalTitle: [''],
    sortTitle: [''],
    year: formBuilder.control<number | null>(null, [Validators.min(MIN_YEAR), Validators.max(MAX_YEAR)]),
    premiered: [''],
    runtime: formBuilder.control<number | null>(null, Validators.min(0)),
    mpaa: [''],
    edition: ['', Validators.maxLength(MAX_LABEL_LENGTH)],
    videoSource: ['', Validators.maxLength(MAX_LABEL_LENGTH)],
    genres: [''],
    tagline: [''],
    outline: [''],
    plot: [''],
    imdbId: ['', Validators.pattern(IMDB_ID_PATTERN)],
    tmdbId: ['', Validators.pattern(TMDB_ID_PATTERN)],
  });
}

export function toFormValue(metadata: MovieMetadata): MovieMetadataForm['value'] {
  return {
    title: metadata.title,
    originalTitle: metadata.originalTitle ?? '',
    sortTitle: metadata.sortTitle ?? '',
    year: metadata.year,
    premiered: metadata.premiered ?? '',
    runtime: metadata.runtime,
    mpaa: metadata.mpaa ?? '',
    edition: metadata.edition ?? '',
    videoSource: metadata.videoSource ?? '',
    genres: metadata.genres.join(`${GENRE_SEPARATOR} `),
    tagline: metadata.tagline ?? '',
    outline: metadata.outline ?? '',
    plot: metadata.plot ?? '',
    imdbId: metadata.imdbId ?? '',
    tmdbId: metadata.tmdbId ?? '',
  };
}

export function toMetadata(form: MovieMetadataForm): MovieMetadata {
  const value = form.getRawValue();
  return {
    title: value.title.trim(),
    originalTitle: textOrNull(value.originalTitle),
    sortTitle: textOrNull(value.sortTitle),
    year: value.year,
    premiered: textOrNull(value.premiered),
    runtime: value.runtime,
    mpaa: textOrNull(value.mpaa),
    edition: textOrNull(value.edition),
    videoSource: textOrNull(value.videoSource),
    genres: value.genres
      .split(GENRE_SEPARATOR)
      .map((genre) => genre.trim())
      .filter((genre) => genre.length > 0),
    tagline: textOrNull(value.tagline),
    outline: textOrNull(value.outline),
    plot: textOrNull(value.plot),
    imdbId: textOrNull(value.imdbId),
    tmdbId: textOrNull(value.tmdbId),
  };
}

function textOrNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}
