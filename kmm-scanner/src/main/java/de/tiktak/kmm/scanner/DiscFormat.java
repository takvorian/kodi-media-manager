package de.tiktak.kmm.scanner;

/**
 * Speicherform eines Films.
 */
public enum DiscFormat {
    /** Einzelne Videodatei (oder mehrteiliger Stapel), z. B. MKV oder ISO. */
    FILE,
    /** DVD-Ordnerstruktur ({@code VIDEO_TS/VIDEO_TS.IFO}). */
    DVD,
    /** Blu-ray-Ordnerstruktur ({@code BDMV/index.bdmv}). */
    BLURAY
}
