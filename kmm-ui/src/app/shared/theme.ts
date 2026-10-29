import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** Die `theme.mp3` eines Films oder einer Serie. */
export interface ThemeInfo {
  readonly exists: boolean;
  readonly path: string;
  readonly sizeBytes: number | null;
  readonly modifiedAt: string | null;
}

/** Ein Titelmusik-Vorschlag; `previewUrl` lässt sich direkt anhören, `webUrl` ist die Seite des Anbieters. */
export interface ThemeOption {
  readonly name: string | null;
  readonly subject: string | null;
  readonly source: string;
  readonly previewUrl: string | null;
  readonly webUrl: string;
  readonly durationSeconds: number | null;
  readonly scraperId: string;
  readonly scraperName: string;
  /** Der Anbieter führt sie unter demselben Titel; nur solche übernimmt KMM automatisch. */
  readonly sameTitle: boolean;
}

/** Vorschläge, gleicher Titel zuerst, und Hinweise zu nicht eingerichteten oder fehlerhaften Anbietern. */
export interface ThemeOptions {
  readonly candidates: readonly ThemeOption[];
  readonly warnings: readonly string[];
}

/** Titelmusik-Endpunkte eines Films oder einer Serie, z. B. `/api/v1/tvshows/3/theme`. */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly http = inject(HttpClient);

  info(endpoint: string): Observable<ThemeInfo> {
    return this.http.get<ThemeInfo>(endpoint);
  }

  candidates(endpoint: string): Observable<ThemeOptions> {
    return this.http.get<ThemeOptions>(`${endpoint}/candidates`);
  }

  /** Lädt die Musik und legt sie als `theme.mp3` in den Ordner. */
  apply(endpoint: string, option: ThemeOption): Observable<ThemeInfo> {
    return this.http.put<ThemeInfo>(endpoint, option);
  }

  remove(endpoint: string): Observable<ThemeInfo> {
    return this.http.delete<ThemeInfo>(endpoint);
  }

  /** Adresse der Datei zum Anhören; die Änderungszeit umgeht alte Stände im Browser-Cache. */
  fileUrl(endpoint: string, info: ThemeInfo): string {
    return `${endpoint}/file?v=${encodeURIComponent(info.modifiedAt ?? '')}`;
  }
}
