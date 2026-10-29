import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** Ein Trailer-Vorschlag; `url` kommt in die NFO, `webUrl` öffnet ihn im Browser. */
export interface TrailerOption {
  readonly name: string;
  readonly url: string;
  readonly webUrl: string;
  readonly previewUrl: string | null;
  readonly language: string | null;
  readonly type: string;
  readonly official: boolean;
  readonly height: number | null;
  readonly published: string | null;
  readonly scraperName: string;
}

/** Vorschläge, die besten zuerst, und Hinweise zu nicht eingerichteten oder fehlerhaften Anbietern. */
export interface TrailerOptions {
  readonly candidates: readonly TrailerOption[];
  readonly warnings: readonly string[];
}

/** Film oder Serie nach einer Änderung; gebraucht wird nur der Trailer. */
export interface WithTrailer {
  readonly trailer: string | null;
}

/** Trailer-Endpunkte eines Films oder einer Serie, z. B. `/api/v1/movies/5/trailer`. */
@Injectable({ providedIn: 'root' })
export class TrailerService {
  private readonly http = inject(HttpClient);

  candidates(endpoint: string): Observable<TrailerOptions> {
    return this.http.get<TrailerOptions>(`${endpoint}/candidates`);
  }

  /** Trägt den Link in Bibliothek und NFO ein. */
  apply(endpoint: string, url: string): Observable<WithTrailer> {
    return this.http.put<WithTrailer>(endpoint, { url });
  }

  remove(endpoint: string): Observable<WithTrailer> {
    return this.http.delete<WithTrailer>(endpoint);
  }
}
