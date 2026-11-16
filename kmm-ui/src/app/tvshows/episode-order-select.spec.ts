import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSelect } from '@angular/material/select';
import { By } from '@angular/platform-browser';

import { provideI18nTesting } from '../i18n/testing';
import { EpisodeOrderSelect } from './episode-order-select';
import { TvEpisodeOrdering } from './tvshow';
import { TVSHOWS_URL } from './tvshow.service';

describe('EpisodeOrderSelect', () => {
  let fixture: ComponentFixture<EpisodeOrderSelect>;
  let httpTesting: HttpTestingController;
  let chosen: (TvEpisodeOrdering | null)[];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [EpisodeOrderSelect],
      providers: [provideI18nTesting(), provideHttpClient(), provideHttpClientTesting()],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EpisodeOrderSelect);
    fixture.componentRef.setInput('showId', 3);
    chosen = [];
    fixture.componentInstance.chosen.subscribe((ordering) => chosen.push(ordering));
    fixture.detectChanges();
  });

  afterEach(() => httpTesting.verify());

  function select(): MatSelect {
    return fixture.debugElement.query(By.directive(MatSelect)).componentInstance;
  }

  async function open(): Promise<void> {
    select().open();
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function optionTexts(): string[] {
    return select().options.map((option) => option.viewValue);
  }

  it('loads the orders of the providers only when the list opens', async () => {
    httpTesting.expectNone(`${TVSHOWS_URL}/3/episode-orders`);

    await open();
    httpTesting.expectOne(`${TVSHOWS_URL}/3/episode-orders`).flush([
      {
        scraperId: 'tvdb',
        scraperName: 'TheTVDB',
        orders: [
          { id: 'official', name: 'Aired Order' },
          { id: 'alternate', name: 'Netflix Order' },
        ],
      },
    ]);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(optionTexts()).toContain('Netflix Order');
    select().close();
    await fixture.whenStable();
    await open();
    httpTesting.expectNone(`${TVSHOWS_URL}/3/episode-orders`);
  });

  it('reports the chosen order with its name and keeps showing the current one', async () => {
    await open();
    httpTesting.expectOne(`${TVSHOWS_URL}/3/episode-orders`).flush([
      {
        scraperId: 'tvdb',
        scraperName: 'TheTVDB',
        orders: [{ id: 'alternate', name: 'Netflix Order' }],
      },
    ]);
    fixture.detectChanges();
    await fixture.whenStable();

    select()
      .options.find((option) => option.value === 'tvdb:alternate')!
      .select();
    fixture.detectChanges();

    expect(chosen).toEqual([{ scraperId: 'tvdb', orderId: 'alternate', name: 'Netflix Order' }]);
    expect(select().value).toBe('');
  });

  it('shows the own order before the list is loaded', async () => {
    fixture.componentRef.setInput('value', {
      scraperId: 'tmdb',
      orderId: '5eb730dfca7ec6001f7beb51',
      name: 'Netflix Order (Digital)',
    });
    fixture.detectChanges();
    await fixture.whenStable();

    expect(select().value).toBe('tmdb:5eb730dfca7ec6001f7beb51');
    expect(optionTexts()).toContain('Netflix Order (Digital)');
  });

  it('explains that a TV show without provider must be scraped first', async () => {
    await open();
    httpTesting.expectOne(`${TVSHOWS_URL}/3/episode-orders`).flush([]);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(document.querySelector('.ordering-none')).not.toBeNull();
  });

  it('names a provider that knows the TV show but no other order', async () => {
    await open();
    httpTesting
      .expectOne(`${TVSHOWS_URL}/3/episode-orders`)
      .flush([{ scraperId: 'tmdb', scraperName: 'TMDB', orders: [] }]);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(document.querySelector('.ordering-none')).toBeNull();
    expect(document.querySelector('.ordering-single')?.textContent).toContain(
      'Keine weitere Zählweise',
    );
  });

  it('puts the standard order into the section of the scraper and reloads after a change', async () => {
    fixture.componentRef.setInput('scraper', { id: 'tmdb', displayName: 'TMDB' });
    fixture.detectChanges();
    expect(select().triggerValue).toBe('Standard-Zählweise von TMDB');
    await open();
    httpTesting
      .expectOne(`${TVSHOWS_URL}/3/episode-orders`)
      .flush([{ scraperId: 'tmdb', scraperName: 'TMDB', orders: [] }]);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(optionTexts()).toEqual([
      'Standard-Zählweise',
      'Keine weitere Zählweise für diese Serie',
    ]);
    expect(select().options.first.group?.label).toBe('TMDB');
    expect(fixture.nativeElement.querySelector('mat-select-trigger')?.textContent).toContain(
      'Standard-Zählweise von TMDB',
    );

    select().close();
    fixture.componentRef.setInput('scraper', { id: 'tvdb', displayName: 'TheTVDB' });
    fixture.detectChanges();
    await open();
    httpTesting.expectOne(`${TVSHOWS_URL}/3/episode-orders`).flush([]);
    fixture.detectChanges();
    expect(optionTexts()[0]).toBe('Standard-Zählweise von TheTVDB');
  });
});
