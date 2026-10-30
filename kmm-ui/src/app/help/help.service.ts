import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { forkJoin, map, Observable, shareReplay } from 'rxjs';

import { HELP_CHAPTERS, HelpHit, searchChapter } from './help';

/** Lädt die Kapitel des Handbuchs, die mit der Oberfläche unter `help/` ausgeliefert werden. */
@Injectable({ providedIn: 'root' })
export class HelpService {
  private readonly http = inject(HttpClient);
  private readonly chapters = new Map<string, Observable<string>>();

  markdown(slug: string): Observable<string> {
    let chapter = this.chapters.get(slug);
    if (!chapter) {
      chapter = this.http.get(`help/${slug}.md`, { responseType: 'text' }).pipe(shareReplay(1));
      this.chapters.set(slug, chapter);
    }
    return chapter;
  }

  search(query: string): Observable<HelpHit[]> {
    return forkJoin(HELP_CHAPTERS.map((chapter) => this.markdown(chapter.slug))).pipe(
      map((texts) =>
        HELP_CHAPTERS.flatMap(
          (chapter, index) => searchChapter(chapter, texts[index], query) ?? [],
        ),
      ),
    );
  }
}
