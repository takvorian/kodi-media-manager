/** Antwort von GET /api/v1/system/info. */
export interface SystemInfo {
  readonly applicationName: string;
  readonly version: string;
  readonly javaVersion: string;
  readonly activeProfiles: readonly string[];
  readonly database: string;
}
