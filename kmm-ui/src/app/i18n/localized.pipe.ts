import { inject, Pipe, PipeTransform } from '@angular/core';

import { LanguageService } from './language.service';
import { localize, LocalizedText } from './localized-text';

/** Translates a backend text in templates and follows a language switch: `{{ job.title | localized }}`. */
@Pipe({ name: 'localized', pure: false })
export class LocalizedPipe implements PipeTransform {
  private readonly languageService = inject(LanguageService);
  private lastText: LocalizedText | null | undefined;
  private lastLanguage: string | undefined;
  private lastResult = '';

  transform(text: LocalizedText | null | undefined): string {
    const language = this.languageService.current();
    if (text !== this.lastText || language !== this.lastLanguage) {
      this.lastText = text;
      this.lastLanguage = language;
      this.lastResult = localize(text);
    }
    return this.lastResult;
  }
}
