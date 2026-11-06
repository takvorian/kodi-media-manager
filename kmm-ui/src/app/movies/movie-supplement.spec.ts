import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';

import { provideI18nTesting } from '../i18n/testing';
import { EMPTY_SUPPLEMENT, MovieActor, MovieSupplement } from './movie';
import { MovieSupplementView, VISIBLE_ACTORS } from './movie-supplement';

describe('MovieSupplementView', () => {
  const heat: MovieSupplement = {
    actors: [
      { name: 'Al Pacino', role: 'Lt. Vincent Hanna', thumb: null },
      { name: 'Robert De Niro', role: null, thumb: null },
    ],
    directors: ['Michael Mann'],
    writers: ['Michael Mann', 'Frank Darabont'],
    studios: ['Warner Bros.'],
    countries: ['USA'],
    tags: ['Heist'],
    ratings: [
      { source: 'imdb', value: 8.3, max: 10, votes: 650000, defaultRating: true },
      { source: 'eigene', value: 7, max: null, votes: null, defaultRating: false },
    ],
    set: { name: 'Michael Mann Collection', overview: 'Filme von Michael Mann' },
    userRating: 9,
    playCount: 2,
    lastPlayed: '2024-05-01T18:15:00Z',
  };

  let fixture: ComponentFixture<MovieSupplementView>;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideI18nTesting()] });
    fixture = TestBed.createComponent(MovieSupplementView);
    fixture.componentRef.setInput('addedAt', '2016-11-30T00:20:15Z');
  });

  function render(supplement: MovieSupplement): void {
    fixture.componentRef.setInput('supplement', supplement);
    fixture.detectChanges();
  }

  function text(selector: string): string | undefined {
    return (fixture.nativeElement as HTMLElement)
      .querySelector(selector)
      ?.textContent?.replace(/\s+/g, ' ')
      .trim();
  }

  function all(selector: string): Element[] {
    return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll(selector));
  }

  it('shows all known details', () => {
    render(heat);

    expect(text('.movie-set')).toBe('Michael Mann Collection – Filme von Michael Mann');
    expect(all('.ratings li').map((item) => item.textContent?.replace(/\s+/g, ' ').trim())).toEqual(
      ['IMDb 8,3/10 (650.000 Stimmen)', 'eigene 7,0'],
    );
    expect(all('.default-rating')).toHaveLength(1);
    expect(text('.user-rating')).toBe('9/10');
    expect(text('.directors')).toBe('Michael Mann');
    expect(text('.writers')).toBe('Michael Mann, Frank Darabont');
    expect(text('.studios')).toBe('Warner Bros.');
    expect(text('.countries')).toBe('USA');
    expect(text('.tags')).toBe('Heist');
    expect(text('.play-state')).toBe('2×, zuletzt am 01.05.2024');
    expect(all('.actor-name').map((name) => name.textContent)).toEqual([
      'Al Pacino',
      'Robert De Niro',
    ]);
    expect(all('.actor-role').map((role) => role.textContent)).toEqual(['Lt. Vincent Hanna']);
  });

  it('shows only the date added and the play state when nothing else is known', () => {
    render(EMPTY_SUPPLEMENT);

    expect(all('dt').map((label) => label.textContent)).toEqual(['Hinzugefügt', 'Gesehen']);
    expect(text('.added-at')).toBe('30.11.2016');
    expect(text('.play-state')).toBe('noch nicht');
    expect(text('.cast-title')).toBeUndefined();
  });

  it('collapses a long cast', () => {
    const actors: MovieActor[] = Array.from({ length: VISIBLE_ACTORS + 3 }, (_, index) => ({
      name: `Darsteller ${index}`,
      role: null,
      thumb: null,
    }));
    render({ ...EMPTY_SUPPLEMENT, actors });

    expect(all('.actor-name')).toHaveLength(VISIBLE_ACTORS);
    expect(text('.toggle-actors')).toBe('Alle anzeigen (3 weitere)');

    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('.toggle-actors')!
      .click();
    fixture.detectChanges();

    expect(all('.actor-name')).toHaveLength(VISIBLE_ACTORS + 3);
    expect(text('.toggle-actors')).toBe('Weniger anzeigen');
  });

  it('labels the default rating in the active language', () => {
    render({
      ...EMPTY_SUPPLEMENT,
      ratings: [{ source: 'default', value: 6, max: 10, votes: null, defaultRating: true }],
    });

    expect(text('.ratings li')).toBe('Standard 6,0/10');
  });

  it('switches to English at runtime, including dates and numbers', async () => {
    const actors: MovieActor[] = Array.from({ length: VISIBLE_ACTORS + 3 }, (_, index) => ({
      name: `Actor ${index}`,
      role: null,
      thumb: null,
    }));
    render({ ...heat, actors });

    TestBed.inject(TranslocoService).setActiveLang('en');
    await fixture.whenStable();

    expect(all('dt').map((label) => label.textContent)).toContain('Watched');
    expect(all('.ratings li').map((item) => item.textContent?.replace(/\s+/g, ' ').trim())).toEqual(
      ['IMDb 8.3/10 (650,000 votes)', 'eigene 7.0'],
    );
    expect(text('.added-at')).toBe('30 Nov 2016');
    expect(text('.play-state')).toBe('2×, last on 1 May 2024');
    expect(text('.cast-title')).toBe('Cast');
    expect(text('.toggle-actors')).toBe('Show all (3 more)');
  });
});
