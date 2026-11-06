import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { provideRouter, Router, TitleStrategy } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';

import { provideI18nTesting } from './testing';
import { TranslatedTitleStrategy } from './translated-title-strategy';

@Component({ template: '' })
class Page {}

describe('TranslatedTitleStrategy', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'movies', title: 'pageTitle.movies', component: Page },
          { path: 'untitled', component: Page },
        ]),
        provideI18nTesting(),
        { provide: TitleStrategy, useClass: TranslatedTitleStrategy },
      ],
    });
  });

  it('translates the route title and follows a language switch', async () => {
    const title = TestBed.inject(Title);

    await TestBed.inject(Router).navigateByUrl('/movies');
    expect(title.getTitle()).toBe('Filme – KMM');

    TestBed.inject(TranslocoService).setActiveLang('en');
    expect(title.getTitle()).toBe('Movies – KMM');
  });

  it('uses the application name for routes without a title', async () => {
    await TestBed.inject(Router).navigateByUrl('/untitled');

    expect(TestBed.inject(Title).getTitle()).toBe('Kodi Media Manager');
  });
});
