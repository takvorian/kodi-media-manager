import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ArtworkCandidates, ArtworkInfo, ArtworkType } from './artwork';
import { MOVIES_URL } from './movie.service';

@Injectable({ providedIn: 'root' })
export class ArtworkService {
  private readonly http = inject(HttpClient);

  findAll(movieId: number): Observable<ArtworkInfo[]> {
    return this.http.get<ArtworkInfo[]>(artworkUrl(movieId));
  }

  /** URL des Bildes; die Änderungszeit sorgt dafür, dass der Browser ein ersetztes Bild neu lädt. */
  imageUrl(movieId: number, artwork: ArtworkInfo): string {
    const version = artwork.modifiedAt ? `?v=${encodeURIComponent(artwork.modifiedAt)}` : '';
    return `${artworkUrl(movieId)}/${artwork.type}/image${version}`;
  }

  candidates(movieId: number, type: ArtworkType): Observable<ArtworkCandidates> {
    return this.http.get<ArtworkCandidates>(`${artworkUrl(movieId)}/${type}/candidates`);
  }

  /** Lädt das Bild und speichert es nach Kodi-Konvention neben dem Film. */
  apply(movieId: number, type: ArtworkType, url: string): Observable<ArtworkInfo> {
    return this.http.put<ArtworkInfo>(`${artworkUrl(movieId)}/${type}`, { url });
  }

  remove(movieId: number, type: ArtworkType): Observable<void> {
    return this.http.delete<void>(`${artworkUrl(movieId)}/${type}`);
  }
}

function artworkUrl(movieId: number): string {
  return `${MOVIES_URL}/${movieId}/artwork`;
}
