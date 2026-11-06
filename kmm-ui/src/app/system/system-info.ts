/** Answer of GET /api/v1/system/info. */
export interface SystemInfo {
  /** Null if it cannot be determined. */
  readonly applicationName: string | null;
  readonly version: string;
  readonly javaVersion: string;
  readonly activeProfiles: readonly string[];
  /** Null if it cannot be determined. */
  readonly database: string | null;
}

/** A version from GET /api/v1/system/changelog. */
export interface ChangelogVersion {
  readonly version: string;
  readonly date: string;
  readonly changes: readonly string[];
}
