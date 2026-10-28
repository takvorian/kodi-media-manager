import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Job } from '../jobs/job';
import { TagAssignment, TagSummary, TaggedItems } from './tag';

export const TAGS_URL = '/api/v1/tags';

@Injectable({ providedIn: 'root' })
export class TagService {
  private readonly http = inject(HttpClient);

  findAll(): Observable<TagSummary[]> {
    return this.http.get<TagSummary[]>(TAGS_URL);
  }

  findTagged(tag: string): Observable<TaggedItems> {
    return this.http.get<TaggedItems>(`${TAGS_URL}/items`, { params: { tag } });
  }

  rename(tag: string, newName: string): Observable<Job> {
    return this.http.post<Job>(`${TAGS_URL}/rename`, { tag, newName });
  }

  delete(tag: string): Observable<Job> {
    return this.http.post<Job>(`${TAGS_URL}/delete`, { tag });
  }

  assign(assignment: TagAssignment): Observable<Job> {
    return this.http.post<Job>(`${TAGS_URL}/assign`, assignment);
  }

  unassign(assignment: TagAssignment): Observable<Job> {
    return this.http.post<Job>(`${TAGS_URL}/unassign`, assignment);
  }
}
