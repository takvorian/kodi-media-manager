import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { forkJoin, map, Observable, shareReplay } from 'rxjs';

import { UiLanguage } from '../i18n/ui-language';
import { HELP_CHAPTERS, HelpHit, searchChapter } from './help';

/** Loads the chapters of the manual, which ship with the UI under `help/<language>/`. */
@Injectable({ providedIn: 'root' })
export class HelpService {
  private readonly http = inject(HttpClient);
  private readonly chapters = new Map<string, Observable<string>>();

  markdown(slug: string, language: UiLanguage): Observable<string> {
    const path = `help/${language}/${slug}.md`;
    let chapter = this.chapters.get(path);
    if (!chapter) {
      chapter = this.http.get(path, { responseType: 'text' }).pipe(shareReplay(1));
      this.chapters.set(path, chapter);
    }
    return chapter;
  }

  search(query: string, language: UiLanguage): Observable<HelpHit[]> {
    return forkJoin(HELP_CHAPTERS.map((chapter) => this.markdown(chapter.slug, language))).pipe(
      map((texts) =>
        HELP_CHAPTERS.flatMap(
          (chapter, index) => searchChapter(chapter, texts[index], query, language) ?? [],
        ),
      ),
    );
  }
}
