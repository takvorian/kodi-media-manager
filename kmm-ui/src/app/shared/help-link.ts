import { Component, computed, input } from '@angular/core';

/** Link auf eine Stelle im Handbuch. Öffnet einen neuen Tab, damit Eingaben im Formular erhalten bleiben. */
@Component({
  selector: 'app-help-link',
  styles: ':host { margin-inline-start: 4px; }',
  template: `<a
    class="help-link"
    [href]="href()"
    target="_blank"
    rel="noopener"
    title="Im Handbuch nachlesen (neuer Tab)"
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
