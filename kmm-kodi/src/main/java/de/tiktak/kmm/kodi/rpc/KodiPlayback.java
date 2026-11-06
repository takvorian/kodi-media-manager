package de.tiktak.kmm.kodi.rpc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Playback state and user rating of a movie or an episode in Kodi's library.
 *
 * @param file       file as Kodi sees it, a stack for multi-part movies
 * @param season     season, or {@code null} for movies
 * @param episode    episode, or {@code null} for movies
 * @param playCount  number of plays, 0 for unwatched
 * @param lastPlayed last play in the local time of the Kodi device, or {@code null}
 * @param userRating user rating 1–10, 0 for not rated
 */
public record KodiPlayback(String file, Integer season, Integer episode, int playCount, LocalDateTime lastPlayed,
                           int userRating) {

    public KodiPlayback {
        Objects.requireNonNull(file, "file must not be null");
    }

    /** @return the files of the entry, all parts for multi-part movies */
    public List<String> files() {
        return KodiFiles.parts(file);
    }

    /** @param file the file looked for, as Kodi sees it; for multi-part movies the first part */
    public boolean isMovie(String file) {
        return season == null && KodiFiles.matches(this.file, file);
    }

    public boolean isEpisode(String file, int season, int episode) {
        return this.season != null && this.season == season && this.episode != null && this.episode == episode
                && KodiFiles.matches(this.file, file);
    }
}
