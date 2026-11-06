import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ArtworkCandidates, ArtworkInfo, ArtworkSource, ArtworkType, versioned } from './artwork';
import { MOVIES_URL } from './movie.service';

@Injectable({ providedIn: 'root' })
export class ArtworkService {
  private readonly http = inject(HttpClient);

  /** The artwork of a movie as the source of an artwork gallery. */
  forMovie(movieId: number): ArtworkSource {
    return {
      findAll: () => this.findAll(movieId),
      imageUrl: (artwork) => this.imageUrl(movieId, artwork),
      candidates: (type) => this.candidates(movieId, type),
      apply: (type, url) => this.apply(movieId, type, url),
      remove: (type) => this.remove(movieId, type),
    };
  }

  findAll(movieId: number): Observable<ArtworkInfo[]> {
    return this.http.get<ArtworkInfo[]>(artworkUrl(movieId));
  }

  /** URL of the image; the modification time makes the browser reload a replaced image. */
  imageUrl(movieId: number, artwork: ArtworkInfo): string {
    return versioned(`${artworkUrl(movieId)}/${artwork.type}/image`, artwork);
  }

  candidates(movieId: number, type: ArtworkType): Observable<ArtworkCandidates> {
    return this.http.get<ArtworkCandidates>(`${artworkUrl(movieId)}/${type}/candidates`);
  }

  /** Downloads the image and saves it next to the movie following the Kodi convention. */
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
