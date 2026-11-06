import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import { ArtworkCandidates, ArtworkInfo, ArtworkSource, versioned } from '../movies/artwork';
import { EPISODES_URL, TVSHOWS_URL } from './tvshow.service';

/** Artwork sources for TV shows, seasons and episodes; saved following the Kodi convention. */
@Injectable({ providedIn: 'root' })
export class TvShowArtworkService {
  private readonly http = inject(HttpClient);

  /**
   * @param season season, or `null` for the artwork of the TV show
   */
  forShow(showId: number, season: number | null): ArtworkSource {
    return this.source(`${TVSHOWS_URL}/${showId}/artwork`, season === null ? null : String(season));
  }

  forEpisode(episodeId: number): ArtworkSource {
    return this.source(`${EPISODES_URL}/${episodeId}/artwork`, null);
  }

  private source(baseUrl: string, season: string | null): ArtworkSource {
    const params = season === null ? new HttpParams() : new HttpParams().set('season', season);
    const query = season === null ? '' : `?season=${season}`;
    return {
      findAll: () => this.http.get<ArtworkInfo[]>(baseUrl, { params }),
      imageUrl: (artwork) => versioned(`${baseUrl}/${artwork.type}/image${query}`, artwork),
      candidates: (type) =>
        this.http.get<ArtworkCandidates>(`${baseUrl}/${type}/candidates`, { params }),
      apply: (type, url) => this.http.put<ArtworkInfo>(`${baseUrl}/${type}`, { url }, { params }),
      remove: (type) => this.http.delete<void>(`${baseUrl}/${type}`, { params }),
    };
  }
}
