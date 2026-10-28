import { FormControl, FormGroup, NonNullableFormBuilder, Validators } from '@angular/forms';

import { EpisodeMetadata, TvShowMetadata } from './tvshow';

export const MIN_YEAR = 1870;
export const MAX_YEAR = 2200;
const MAX_TITLE_LENGTH = 512;
const MAX_LABEL_LENGTH = 255;
const NUMERIC_ID_PATTERN = /^\d{1,10}$/;
const IMDB_ID_PATTERN = /^tt\d{7,10}$/;
const LIST_SEPARATOR = ',';
const MIN_USER_RATING = 1;
const MAX_USER_RATING = 10;

/** Textfelder sind leer statt `null`; leere Eingaben gelten beim Speichern als „nicht gesetzt“. */
export type TvShowMetadataForm = FormGroup<{
  title: FormControl<string>;
  originalTitle: FormControl<string>;
  sortTitle: FormControl<string>;
  year: FormControl<number | null>;
  premiered: FormControl<string>;
  runtime: FormControl<number | null>;
  mpaa: FormControl<string>;
  status: FormControl<string>;
  genres: FormControl<string>;
  studios: FormControl<string>;
  tags: FormControl<string>;
  tvdbId: FormControl<string>;
  tmdbId: FormControl<string>;
  imdbId: FormControl<string>;
  userRating: FormControl<number | null>;
  plot: FormControl<string>;
}>;

export type EpisodeMetadataForm = FormGroup<{
  title: FormControl<string>;
  originalTitle: FormControl<string>;
  aired: FormControl<string>;
  runtime: FormControl<number | null>;
  tvdbId: FormControl<string>;
  tmdbId: FormControl<string>;
  imdbId: FormControl<string>;
  userRating: FormControl<number | null>;
  videoSource: FormControl<string>;
  plot: FormControl<string>;
}>;

export function createTvShowMetadataForm(formBuilder: NonNullableFormBuilder): TvShowMetadataForm {
  return formBuilder.group({
    title: ['', [Validators.required, Validators.maxLength(MAX_TITLE_LENGTH)]],
    originalTitle: [''],
    sortTitle: [''],
    year: formBuilder.control<number | null>(null, [Validators.min(MIN_YEAR), Validators.max(MAX_YEAR)]),
    premiered: [''],
    runtime: formBuilder.control<number | null>(null, Validators.min(0)),
    mpaa: [''],
    status: [''],
    genres: [''],
    studios: [''],
    tags: [''],
    tvdbId: ['', Validators.pattern(NUMERIC_ID_PATTERN)],
    tmdbId: ['', Validators.pattern(NUMERIC_ID_PATTERN)],
    imdbId: ['', Validators.pattern(IMDB_ID_PATTERN)],
    userRating: formBuilder.control<number | null>(null, userRatingValidators()),
    plot: [''],
  });
}

export function createEpisodeMetadataForm(formBuilder: NonNullableFormBuilder): EpisodeMetadataForm {
  return formBuilder.group({
    title: ['', [Validators.required, Validators.maxLength(MAX_TITLE_LENGTH)]],
    originalTitle: [''],
    aired: [''],
    runtime: formBuilder.control<number | null>(null, Validators.min(0)),
    tvdbId: ['', Validators.pattern(NUMERIC_ID_PATTERN)],
    tmdbId: ['', Validators.pattern(NUMERIC_ID_PATTERN)],
    imdbId: ['', Validators.pattern(IMDB_ID_PATTERN)],
    userRating: formBuilder.control<number | null>(null, userRatingValidators()),
    videoSource: ['', Validators.maxLength(MAX_LABEL_LENGTH)],
    plot: [''],
  });
}

export function toTvShowFormValue(metadata: TvShowMetadata): TvShowMetadataForm['value'] {
  return {
    title: metadata.title,
    originalTitle: metadata.originalTitle ?? '',
    sortTitle: metadata.sortTitle ?? '',
    year: metadata.year,
    premiered: metadata.premiered ?? '',
    runtime: metadata.runtime,
    mpaa: metadata.mpaa ?? '',
    status: metadata.status ?? '',
    genres: joinList(metadata.genres),
    studios: joinList(metadata.studios),
    tags: joinList(metadata.tags),
    tvdbId: metadata.tvdbId ?? '',
    tmdbId: metadata.tmdbId ?? '',
    imdbId: metadata.imdbId ?? '',
    userRating: metadata.userRating,
    plot: metadata.plot ?? '',
  };
}

export function toTvShowMetadata(form: TvShowMetadataForm): TvShowMetadata {
  const value = form.getRawValue();
  return {
    title: value.title.trim(),
    originalTitle: textOrNull(value.originalTitle),
    sortTitle: textOrNull(value.sortTitle),
    year: value.year,
    premiered: textOrNull(value.premiered),
    runtime: value.runtime,
    mpaa: textOrNull(value.mpaa),
    status: textOrNull(value.status),
    genres: splitList(value.genres),
    studios: splitList(value.studios),
    tags: splitList(value.tags),
    tvdbId: textOrNull(value.tvdbId),
    tmdbId: textOrNull(value.tmdbId),
    imdbId: textOrNull(value.imdbId),
    userRating: value.userRating,
    plot: textOrNull(value.plot),
  };
}

export function toEpisodeFormValue(metadata: EpisodeMetadata): EpisodeMetadataForm['value'] {
  return {
    title: metadata.title,
    originalTitle: metadata.originalTitle ?? '',
    aired: metadata.aired ?? '',
    runtime: metadata.runtime,
    tvdbId: metadata.tvdbId ?? '',
    tmdbId: metadata.tmdbId ?? '',
    imdbId: metadata.imdbId ?? '',
    userRating: metadata.userRating,
    videoSource: metadata.videoSource ?? '',
    plot: metadata.plot ?? '',
  };
}

export function toEpisodeMetadata(form: EpisodeMetadataForm): EpisodeMetadata {
  const value = form.getRawValue();
  return {
    title: value.title.trim(),
    originalTitle: textOrNull(value.originalTitle),
    aired: textOrNull(value.aired),
    runtime: value.runtime,
    tvdbId: textOrNull(value.tvdbId),
    tmdbId: textOrNull(value.tmdbId),
    imdbId: textOrNull(value.imdbId),
    userRating: value.userRating,
    videoSource: textOrNull(value.videoSource),
    plot: textOrNull(value.plot),
  };
}

function userRatingValidators() {
  return [Validators.min(MIN_USER_RATING), Validators.max(MAX_USER_RATING)];
}

function joinList(values: readonly string[]): string {
  return values.join(`${LIST_SEPARATOR} `);
}

function splitList(value: string): string[] {
  return value
    .split(LIST_SEPARATOR)
    .map((entry) => entry.trim())
    .filter((entry) => entry.length > 0);
}

function textOrNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}
