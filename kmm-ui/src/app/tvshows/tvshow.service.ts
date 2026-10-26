import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { EpisodeDetails, EpisodeMetadata, TvShowDetails, TvShowMetadata, TvShowPage, TvShowQuery } from './tvshow';

export const TVSHOWS_URL = '/api/v1/tvshows';
export const EPISODES_URL = '/api/v1/episodes';
const SORT_BY_TITLE = 'title,asc';

@Injectable({ providedIn: 'root' })
export class TvShowService {
  private readonly http = inject(HttpClient);

  findShows(query: TvShowQuery): Observable<TvShowPage> {
    let params = new HttpParams().set('page', query.page).set('size', query.size).set('sort', SORT_BY_TITLE);
    const search = query.search.trim();
    if (search) {
      params = params.set('search', search);
    }
    return this.http.get<TvShowPage>(TVSHOWS_URL, { params });
  }

  findShow(showId: number): Observable<TvShowDetails> {
    return this.http.get<TvShowDetails>(`${TVSHOWS_URL}/${showId}`);
  }

  /** Speichert die Metadaten; das Backend schreibt sie zugleich in die tvshow.nfo. */
  updateShow(showId: number, metadata: TvShowMetadata): Observable<TvShowDetails> {
    return this.http.put<TvShowDetails>(`${TVSHOWS_URL}/${showId}/metadata`, metadata);
  }

  findEpisode(episodeId: number): Observable<EpisodeDetails> {
    return this.http.get<EpisodeDetails>(`${EPISODES_URL}/${episodeId}`);
  }

  /** Speichert die Metadaten; das Backend schreibt sie zugleich in die NFO der Episode. */
  updateEpisode(episodeId: number, metadata: EpisodeMetadata): Observable<EpisodeDetails> {
    return this.http.put<EpisodeDetails>(`${EPISODES_URL}/${episodeId}/metadata`, metadata);
  }
}
