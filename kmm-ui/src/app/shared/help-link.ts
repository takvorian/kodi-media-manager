import { Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

/** Link to a place in the manual. Opens a new tab so input in the form is kept. */
@Component({
  selector: 'app-help-link',
  imports: [TranslocoPipe],
  styles: ':host { margin-inline-start: 4px; }',
  template: `<a
    class="help-link"
    [href]="href()"
    target="_blank"
    rel="noopener"
    [title]="'shared.helpLink.title' | transloco"
    ><ng-content
  /></a>`,
})
export class HelpLink {
  readonly chapter = input.required<string>();
  readonly anchor = input<string>();

  protected readonly href = computed(() => {
    const anchor = this.anchor();
    return `/help/${this.chapter()}${anchor ? `#${anchor}` : ''}`;
  });
}
