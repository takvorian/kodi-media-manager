package de.tiktak.kmm.scanner;

/**
 * Storage format of a movie.
 */
public enum DiscFormat {
    /** Single video file (or multi-part stack), e.g. MKV or ISO. */
    FILE,
    /** DVD folder structure ({@code VIDEO_TS/VIDEO_TS.IFO}). */
    DVD,
    /** Blu-ray folder structure ({@code BDMV/index.bdmv}). */
    BLURAY
}
