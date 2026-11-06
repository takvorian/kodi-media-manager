import {
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  input,
  signal,
  ViewEncapsulation,
} from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { DomSanitizer } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { map, of } from 'rxjs';

import { HELP_CHAPTERS } from './help';
import { renderHelp } from './help-markdown';
import { HelpService } from './help.service';

const MIN_QUERY_LENGTH = 2;

/** Shows the manual: table of contents with search and the chosen chapter. */
@Component({
  selector: 'app-help-page',
  imports: [RouterLink, MatButtonModule, MatFormFieldModule, MatInputModule, TranslocoPipe],
  templateUrl: './help-page.html',
  styleUrl: './help-page.scss',
  // The styles apply to the HTML from the manual, which Angular does not mark with style attributes.
  encapsulation: ViewEncapsulation.None,
})
export class HelpPage {
  private readonly helpService = inject(HelpService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly router = inject(Router);
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly fragment = toSignal(inject(ActivatedRoute).fragment);

  readonly chapter = input.required<string>();

  protected readonly chapters = HELP_CHAPTERS;
  protected readonly query = signal('');
  protected readonly content = rxResource({
    params: () => this.chapter(),
    // The manual ships with the application; otherwise the sanitizer would remove the anchors of the headings.
    stream: ({ params }) =>
      this.helpService
        .markdown(params)
        .pipe(map((markdown) => this.sanitizer.bypassSecurityTrustHtml(renderHelp(markdown)))),
  });
  protected readonly hits = rxResource({
    params: () => this.query().trim(),
    stream: ({ params }) =>
      params.length < MIN_QUERY_LENGTH ? of(null) : this.helpService.search(params),
  });
  protected readonly position = computed(() =>
    HELP_CHAPTERS.findIndex((chapter) => chapter.slug === this.chapter()),
  );
  protected readonly previous = computed(() => HELP_CHAPTERS[this.position() - 1] ?? null);
  protected readonly next = computed(() =>
    this.position() < 0 ? null : (HELP_CHAPTERS[this.position() + 1] ?? null),
  );

  constructor() {
    effect(() => {
      if (this.content.hasValue()) {
        this.scrollTo(this.fragment());
      }
    });
  }

  /** Links within the manual stay in the application instead of reloading the page. */
  protected followLink(event: MouseEvent): void {
    const link = (event.target as HTMLElement).closest('a');
    const href = link?.getAttribute('href');
    if (href?.startsWith('/help/')) {
      event.preventDefault();
      void this.router.navigateByUrl(href);
    }
  }

  private scrollTo(fragment: string | null | undefined): void {
    setTimeout(() => {
      const target = fragment
        ? this.element.nativeElement.querySelector(`[id="${CSS.escape(fragment)}"]`)
        : null;
      if (target) {
        target.scrollIntoView();
      } else {
        window.scrollTo({ top: 0 });
      }
    });
  }
}
