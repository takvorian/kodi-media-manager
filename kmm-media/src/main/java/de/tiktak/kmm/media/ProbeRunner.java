package de.tiktak.kmm.media;

/**
 * Ruft ffprobe für eine Eingabe auf und liefert dessen JSON-Ausgabe.
 */
@FunctionalInterface
public interface ProbeRunner {

    /**
     * @param input Pfad einer Datei oder eine ffmpeg-Eingabe wie {@code concat:a.vob|b.vob}
     * @throws MediaProbeException wenn ffprobe fehlt, scheitert oder zu lange braucht
     */
    String run(String input);
}
