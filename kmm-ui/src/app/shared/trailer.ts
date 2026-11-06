import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** A trailer suggestion; `url` goes into the NFO, `webUrl` opens it in the browser. */
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

/** Suggestions, best first, and notes on providers that are not set up or failed. */
export interface TrailerOptions {
  readonly candidates: readonly TrailerOption[];
  readonly warnings: readonly string[];
}

/** Movie or TV show after a change; only the trailer is needed. */
export interface WithTrailer {
  readonly trailer: string | null;
}

/** Trailer endpoints of a movie or TV show, e.g. `/api/v1/movies/5/trailer`. */
@Injectable({ providedIn: 'root' })
export class TrailerService {
  private readonly http = inject(HttpClient);

  candidates(endpoint: string): Observable<TrailerOptions> {
    return this.http.get<TrailerOptions>(`${endpoint}/candidates`);
  }

  /** Writes the link into the library and the NFO. */
  apply(endpoint: string, url: string): Observable<WithTrailer> {
    return this.http.put<WithTrailer>(endpoint, { url });
  }

  remove(endpoint: string): Observable<WithTrailer> {
    return this.http.delete<WithTrailer>(endpoint);
  }
}
