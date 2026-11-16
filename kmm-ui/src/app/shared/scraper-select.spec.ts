import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSelect } from '@angular/material/select';
import { By } from '@angular/platform-browser';

import { provideI18nTesting } from '../i18n/testing';
import { literal } from '../i18n/localized-text';
import { ScraperChoice } from '../movies/scraper';
import { ScraperSelect } from './scraper-select';

const CHOICE: ScraperChoice = {
  scraperId: null,
  defaultScraperId: 'tvdb',
  scrapers: [
    { id: 'tvdb', displayName: 'TheTVDB', mediaTypes: ['TV_SHOW'], missingConfiguration: null },
    { id: 'tmdb', displayName: 'TMDB', mediaTypes: ['TV_SHOW'], missingConfiguration: null },
    {
      id: 'anidb',
      displayName: 'AniDB',
      mediaTypes: ['TV_SHOW'],
      missingConfiguration: literal('Key fehlt'),
    },
  ],
};

describe('ScraperSelect', () => {
  let fixture: ComponentFixture<ScraperSelect>;
  let chosen: (string | null)[];

  beforeEach(async () => {
    TestBed.configureTestingModule({ imports: [ScraperSelect], providers: [provideI18nTesting()] });
    fixture = TestBed.createComponent(ScraperSelect);
    chosen = [];
    fixture.componentInstance.chosen.subscribe((scraperId) => chosen.push(scraperId));
    fixture.componentRef.setInput('choice', CHOICE);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  function select(): MatSelect {
    return fixture.debugElement.query(By.directive(MatSelect)).componentInstance;
  }

  it('names the scraper of the source and offers only scrapers that are set up', () => {
    expect(select().value).toBe('');
    expect(select().options.map((option) => option.viewValue)).toEqual([
      'Standard der Quelle (TheTVDB)',
      'TheTVDB',
      'TMDB',
      'AniDB',
    ]);
    expect(select().options.get(3)!.disabled).toBe(true);
  });

  it('reports a choice and keeps showing the current scraper', () => {
    select()
      .options.find((option) => option.value === 'tmdb')!
      .select();
    fixture.detectChanges();

    expect(chosen).toEqual(['tmdb']);
    expect(select().value).toBe('');
  });

  it('reports the default of the source as null', async () => {
    fixture.componentRef.setInput('choice', { ...CHOICE, scraperId: 'tmdb' });
    fixture.detectChanges();
    await fixture.whenStable();

    select()
      .options.find((option) => option.value === '')!
      .select();

    expect(chosen).toEqual([null]);
  });

  it('shows the own scraper of the entry', async () => {
    fixture.componentRef.setInput('choice', { ...CHOICE, scraperId: 'tmdb' });
    fixture.detectChanges();
    await fixture.whenStable();

    expect(select().value).toBe('tmdb');
  });
});
