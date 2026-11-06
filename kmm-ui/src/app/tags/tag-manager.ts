import { Component, computed, inject, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { Observable, filter } from 'rxjs';

import { Job } from '../jobs/job';
import { JobStore } from '../jobs/job-store';
import { MovieService } from '../movies/movie.service';
import { CONFIRM } from '../shared/confirm';
import { problemMessage } from '../shared/problem-message';
import { TvShowService } from '../tvshows/tvshow.service';
import { TAG_JOB_KEY, TagSummary, TaggedItem, tagJobSummary } from './tag';
import { TagService } from './tag.service';
import { LocalizedPipe } from '../i18n/localized.pipe';

const SNACKBAR_DURATION_MS = 8000;
const SEARCH_SIZE = 20;

/** A search result to assign the tag to. */
interface Candidate {
  readonly kind: 'movie' | 'show';
  readonly id: number;
  /** Title, for movies with the year; the template marks TV shows in the active language. */
  readonly label: string;
}

/**
 * Tag manager as in Ember: creates (by assigning), renames, deletes, assigns and removes tags of movies and TV
 * shows. Changes run as a background job because they write NFOs.
 */
@Component({
  selector: 'app-tag-manager',
  imports: [
    LocalizedPipe,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    TranslocoPipe,
  ],
  templateUrl: './tag-manager.html',
  styleUrl: './tag-manager.scss',
})
export class TagManager {
  private readonly tagService = inject(TagService);
  private readonly movieService = inject(MovieService);
  private readonly tvShowService = inject(TvShowService);
  private readonly jobStore = inject(JobStore);
  private readonly snackBar = inject(MatSnackBar);
  private readonly confirm = inject(CONFIRM);
  private readonly transloco = inject(TranslocoService);

  protected readonly tags = rxResource({ stream: () => this.tagService.findAll() });
  protected readonly selected = signal<string | null>(null);
  protected readonly tagged = rxResource({
    params: () => this.selected() ?? undefined,
    stream: ({ params }) => this.tagService.findTagged(params),
  });
  protected readonly newName = signal('');
  protected readonly removal = signal<ReadonlySet<string>>(new Set());
  protected readonly search = signal('');
  protected readonly candidates = signal<readonly Candidate[]>([]);
  protected readonly chosen = signal<ReadonlySet<string>>(new Set());
  protected readonly runningJob = computed(() => this.jobStore.runningJob(TAG_JOB_KEY));

  constructor() {
    this.jobStore.connect();
    this.jobStore.completed
      .pipe(
        filter((job) => job.key === TAG_JOB_KEY),
        takeUntilDestroyed(),
      )
      .subscribe((job) => {
        this.notify(tagJobSummary(job));
        this.tags.reload();
        if (this.selected()) {
          this.tagged.reload();
        }
      });
  }

  protected select(tag: TagSummary): void {
    this.selected.set(tag.name);
    this.newName.set(tag.name);
    this.removal.set(new Set());
  }

  /** A new tag only comes into being when it is assigned to something. */
  protected startNewTag(name: string): void {
    const tag = name.trim();
    if (tag) {
      this.selected.set(tag);
      this.newName.set(tag);
      this.removal.set(new Set());
    }
  }

  protected rename(): void {
    const tag = this.selected();
    const newName = this.newName().trim();
    if (tag && newName && newName !== tag) {
      this.track(this.tagService.rename(tag, newName), () => this.selected.set(newName));
    }
  }

  protected delete(): void {
    const tag = this.selected();
    if (tag && this.confirm(this.transloco.translate('tags.confirmDelete', { tag }))) {
      this.track(this.tagService.delete(tag), () => this.selected.set(null));
    }
  }

  protected toggleRemoval(kind: 'movie' | 'show', item: TaggedItem): void {
    this.removal.update((current) => toggled(current, `${kind}:${item.id}`));
  }

  protected isMarked(kind: 'movie' | 'show', item: TaggedItem): boolean {
    return this.removal().has(`${kind}:${item.id}`);
  }

  protected removeMarked(): void {
    const tag = this.selected();
    if (tag && this.removal().size > 0) {
      this.track(this.tagService.unassign({ tag, ...ids(this.removal()) }), () =>
        this.removal.set(new Set()),
      );
    }
  }

  protected find(): void {
    const search = this.search().trim();
    if (!search) {
      this.candidates.set([]);
      return;
    }
    this.movieService.findMovies({ search, page: 0, size: SEARCH_SIZE }).subscribe({
      next: (page) =>
        this.candidates.update((current) => [
          ...page.content.map((movie): Candidate => ({
            kind: 'movie',
            id: movie.id,
            label: movie.year ? `${movie.title} (${movie.year})` : movie.title,
          })),
          ...current.filter((candidate) => candidate.kind === 'show'),
        ]),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
    this.tvShowService.findShows({ search, page: 0, size: SEARCH_SIZE }).subscribe({
      next: (page) =>
        this.candidates.update((current) => [
          ...current.filter((candidate) => candidate.kind === 'movie'),
          ...page.content.map((show): Candidate => ({
            kind: 'show',
            id: show.id,
            label: show.title,
          })),
        ]),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  protected toggleChosen(candidate: Candidate): void {
    this.chosen.update((current) => toggled(current, `${candidate.kind}:${candidate.id}`));
  }

  protected isChosen(candidate: Candidate): boolean {
    return this.chosen().has(`${candidate.kind}:${candidate.id}`);
  }

  protected assignChosen(): void {
    const tag = this.selected();
    if (tag && this.chosen().size > 0) {
      this.track(this.tagService.assign({ tag, ...ids(this.chosen()) }), () =>
        this.chosen.set(new Set()),
      );
    }
  }

  private track(start: Observable<Job>, onStarted: () => void = () => undefined): void {
    start.subscribe({
      next: (job) => {
        this.jobStore.update(job);
        onStarted();
      },
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  private notify(message: string): void {
    this.snackBar.open(message, 'OK', { duration: SNACKBAR_DURATION_MS });
  }
}

function toggled(current: ReadonlySet<string>, key: string): ReadonlySet<string> {
  const next = new Set(current);
  if (!next.delete(key)) {
    next.add(key);
  }
  return next;
}

/** Splits keys like `movie:12` into movie and TV show IDs. */
function ids(keys: ReadonlySet<string>): { movieIds: number[]; showIds: number[] } {
  const parse = (kind: string) =>
    [...keys]
      .filter((key) => key.startsWith(`${kind}:`))
      .map((key) => Number(key.slice(kind.length + 1)));
  return { movieIds: parse('movie'), showIds: parse('show') };
}
