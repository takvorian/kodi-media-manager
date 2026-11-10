package de.tiktak.kmm.kodi.rpc;

import java.util.Objects;

/**
 * Episode in Kodi's library.
 *
 * @param id   Kodi's ID of the episode
 * @param file file as Kodi sees it; several episodes share it in a multi-episode file
 */
public record KodiEpisode(int id, String file, int season, int episode) {

    public KodiEpisode {
        Objects.requireNonNull(file, "file must not be null");
    }

    /** @param file the file looked for, as Kodi sees it */
    public boolean is(String file, int season, int episode) {
        return this.season == season && this.episode == episode && KodiFiles.matches(this.file, file);
    }

    /** @param directory directory as Kodi sees it, with a trailing separator */
    public boolean liesIn(String directory) {
        return KodiFiles.parts(file).stream()
                .anyMatch(part -> KodiFiles.directoryOf(part).equalsIgnoreCase(directory));
    }
}
