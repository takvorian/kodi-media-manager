import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

/** Switches between the general settings and those of the scrapers. */
@Component({
  selector: 'app-settings-tabs',
  imports: [MatTabsModule, RouterLink, RouterLinkActive, TranslocoPipe],
  template: `
    <nav mat-tab-nav-bar [tabPanel]="panel" class="settings-tabs">
      <a
        mat-tab-link
        routerLink="/settings"
        routerLinkActive
        #general="routerLinkActive"
        [routerLinkActiveOptions]="{ exact: true }"
        [active]="general.isActive"
        >{{ 'settings.tabs.general' | transloco }}</a
      >
      <a
        mat-tab-link
        routerLink="/settings/scrapers"
        routerLinkActive
        #scrapers="routerLinkActive"
        [active]="scrapers.isActive"
        >{{ 'settings.tabs.scrapers' | transloco }}</a
      >
    </nav>
    <mat-tab-nav-panel #panel />
  `,
  styles: `
    .settings-tabs {
      margin-bottom: 24px;
      max-width: 900px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SettingsTabs {}
