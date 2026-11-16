import { LocalizedText } from '../../i18n/localized-text';
import { Attribution } from '../../movies/scraper';

/** What a scraper delivers; the order of the scrapers can be chosen per kind. */
export type ScraperKind = 'METADATA' | 'ARTWORK' | 'ENRICHMENT' | 'THEME' | 'TRAILER' | 'MOVIE_SET';

export const SCRAPER_KINDS: readonly ScraperKind[] = [
  'METADATA',
  'ARTWORK',
  'ENRICHMENT',
  'THEME',
  'TRAILER',
  'MOVIE_SET',
];

export interface Capability {
  readonly kind: ScraperKind;
  readonly mediaTypes: readonly string[];
}

/** A scraper plugin in the overview; a switched off plugin has no capabilities and its ID as name. */
export interface ScraperPlugin {
  readonly id: string;
  readonly displayName: string;
  readonly version: string | null;
  readonly description: string | null;
  readonly enabled: boolean;
  readonly capabilities: readonly Capability[];
  readonly missingConfiguration: LocalizedText | null;
  readonly attribution: Attribution | null;
}

export type PluginSettingType = 'TEXT' | 'SECRET' | 'NUMBER';

/** A setting of a plugin; keys are never delivered, only whether one is stored and its last characters. */
export interface PluginSetting {
  readonly name: string;
  readonly type: PluginSettingType;
  readonly label: LocalizedText;
  /** The stored value, `null` while the configuration or the plugin's default applies; always `null` for keys. */
  readonly value: string | null;
  /** Value without a stored one; always `null` for keys. */
  readonly defaultValue: string | null;
  readonly set: boolean;
  readonly hint: string | null;
}

export interface ScraperPluginDetails {
  readonly scraper: ScraperPlugin;
  readonly settings: readonly PluginSetting[];
}
