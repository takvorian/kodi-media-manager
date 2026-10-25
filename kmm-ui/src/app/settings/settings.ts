/** Einstellungen, die durch die Server-Konfiguration festgelegt sein können. */
export type SettingsField = 'METADATA_LANGUAGE' | 'ARTWORK_LANGUAGE' | 'PROXY';

export interface ProxyView {
  readonly host: string;
  readonly port: number;
  readonly username: string | null;
  readonly passwordSet: boolean;
}

/** Antwort von GET /api/v1/settings. */
export interface SettingsView {
  readonly metadataLanguage: string;
  readonly artworkLanguage: string;
  readonly videoExtensions: readonly string[];
  readonly excludedPaths: readonly string[];
  readonly proxy: ProxyView | null;
  readonly overridden: readonly SettingsField[];
}

/** Proxy in einer Änderung; ein Passwort von {@code null} lässt das gespeicherte unverändert. */
export interface ProxySettings {
  readonly host: string;
  readonly port: number;
  readonly username: string | null;
  readonly password: string | null;
}

/** Inhalt von PUT /api/v1/settings. */
export interface KmmSettings {
  readonly metadataLanguage: string;
  readonly artworkLanguage: string;
  readonly videoExtensions: readonly string[];
  readonly excludedPaths: readonly string[];
  readonly proxy: ProxySettings | null;
}
