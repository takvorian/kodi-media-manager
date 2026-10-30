/** Antwort von GET /api/v1/system/info. */
export interface SystemInfo {
  readonly applicationName: string;
  readonly version: string;
  readonly javaVersion: string;
  readonly activeProfiles: readonly string[];
  readonly database: string;
}

/** Eine Version aus GET /api/v1/system/changelog. */
export interface ChangelogVersion {
  readonly version: string;
  readonly date: string;
  readonly changes: readonly string[];
}
