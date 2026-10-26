package de.tiktak.kmm.kodi.rpc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Wiedergabestand eines Films oder einer Episode in Kodis Bibliothek.
 *
 * @param file       Datei aus Sicht von Kodi, bei mehrteiligen Filmen ein Stapel
 * @param season     Staffel oder {@code null} bei Filmen
 * @param episode    Episode oder {@code null} bei Filmen
 * @param playCount  Anzahl der Wiedergaben, 0 für ungesehen
 * @param lastPlayed letzte Wiedergabe in der Ortszeit des Kodi-Geräts oder {@code null}
 */
public record KodiPlayback(String file, Integer season, Integer episode, int playCount, LocalDateTime lastPlayed) {

    public KodiPlayback {
        Objects.requireNonNull(file, "file must not be null");
    }

    /** @return die Dateien des Eintrags, bei mehrteiligen Filmen alle Teile */
    public List<String> files() {
        return KodiFiles.parts(file);
    }

    /** @param file gesuchte Datei aus Sicht von Kodi; bei mehrteiligen Filmen der erste Teil */
    public boolean isMovie(String file) {
        return season == null && KodiFiles.matches(this.file, file);
    }

    public boolean isEpisode(String file, int season, int episode) {
        return this.season != null && this.season == season && this.episode != null && this.episode == episode
                && KodiFiles.matches(this.file, file);
    }
}
