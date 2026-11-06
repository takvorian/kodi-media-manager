import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { TranslocoPipe } from '@jsverse/transloco';

import { LanguageService } from './language.service';
import { UI_LANGUAGE_NAMES, UI_LANGUAGES, UiLanguage } from './ui-language';

/** Button in the toolbar that switches the interface language at runtime. */
@Component({
  selector: 'app-language-switch',
  imports: [MatButtonModule, MatMenuModule, TranslocoPipe],
  template: `
    <button
      matButton
      class="language-switch"
      [matMenuTriggerFor]="languages"
      [title]="'language.switch' | transloco"
      [attr.aria-label]="'language.switch' | transloco"
    >
      {{ languageService.current().toUpperCase() }}
    </button>
    <mat-menu #languages="matMenu">
      @for (language of languageList; track language) {
        <button
          mat-menu-item
          [attr.lang]="language"
          [attr.aria-current]="language === languageService.current() ? 'true' : null"
          (click)="languageService.use(language)"
        >
          {{ names[language] }}
        </button>
      }
    </mat-menu>
  `,
})
export class LanguageSwitch {
  protected readonly languageService = inject(LanguageService);
  protected readonly languageList: readonly UiLanguage[] = UI_LANGUAGES;
  protected readonly names = UI_LANGUAGE_NAMES;
}
