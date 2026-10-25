/** Zuordnung eines Verzeichnisses aus Sicht von KMM zu demselben Verzeichnis aus Sicht von Kodi. */
export interface KodiPathMapping {
  readonly localPath: string;
  readonly kodiPath: string;
}

/** Ein Kodi-Gerät, wie das Backend es liefert (ohne Passwort). */
export interface KodiHost {
  readonly id: number;
  readonly name: string;
  readonly address: string;
  readonly port: number;
  readonly username: string | null;
  readonly passwordSet: boolean;
  readonly realtimeSync: boolean;
  readonly pathMappings: readonly KodiPathMapping[];
}

/** Anlegen oder Ändern; ein fehlendes Passwort (`null`) behält das gespeicherte. */
export interface KodiHostCommand {
  readonly name: string;
  readonly address: string;
  readonly port: number;
  readonly username: string | null;
  readonly password: string | null;
  readonly realtimeSync: boolean;
  readonly pathMappings: readonly KodiPathMapping[];
}

export interface KodiConnectionTest {
  readonly version: string;
}
