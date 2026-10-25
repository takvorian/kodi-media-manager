import { TestBed } from '@angular/core/testing';
import { NonNullableFormBuilder } from '@angular/forms';

import { MovieMetadata } from './movie';
import { MovieMetadataForm, createMovieMetadataForm, toFormValue, toMetadata } from './movie-metadata-form';

describe('movie metadata form', () => {
  const heat: MovieMetadata = {
    title: 'Heat',
    originalTitle: 'Heat',
    sortTitle: null,
    year: 1995,
    premiered: '1995-12-15',
    plot: 'Handlung',
    outline: null,
    tagline: null,
    runtime: 170,
    mpaa: 'FSK 16',
    genres: ['Krimi', 'Drama'],
    imdbId: 'tt0113277',
    tmdbId: null,
  };

  let form: MovieMetadataForm;

  beforeEach(() => {
    form = createMovieMetadataForm(TestBed.inject(NonNullableFormBuilder));
  });

  it('round-trips metadata through the form', () => {
    form.reset(toFormValue(heat));

    expect(toMetadata(form)).toEqual(heat);
    expect(form.controls.genres.value).toBe('Krimi, Drama');
  });

  it('turns blank texts into null and splits genres', () => {
    form.reset(toFormValue(heat));
    form.patchValue({ originalTitle: '  ', genres: ' Action ,, Thriller ,', title: ' Heat 2 ', year: null });

    const metadata = toMetadata(form);

    expect(metadata.originalTitle).toBeNull();
    expect(metadata.genres).toEqual(['Action', 'Thriller']);
    expect(metadata.title).toBe('Heat 2');
    expect(metadata.year).toBeNull();
  });

  it('requires a title', () => {
    form.reset(toFormValue({ ...heat, title: '' }));

    expect(form.controls.title.hasError('required')).toBe(true);
  });

  it('validates year range and id formats', () => {
    form.reset(toFormValue(heat));
    form.patchValue({ year: 1800, imdbId: '0113277', tmdbId: 'abc', runtime: -1 });

    expect(form.controls.year.invalid).toBe(true);
    expect(form.controls.imdbId.invalid).toBe(true);
    expect(form.controls.tmdbId.invalid).toBe(true);
    expect(form.controls.runtime.invalid).toBe(true);
  });

  it('accepts valid ids', () => {
    form.reset(toFormValue({ ...heat, tmdbId: '949' }));

    expect(form.valid).toBe(true);
  });
});
