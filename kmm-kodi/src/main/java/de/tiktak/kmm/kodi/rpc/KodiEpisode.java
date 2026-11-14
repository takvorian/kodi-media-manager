package de.tiktak.kmm.kodi.rpc;

import java.util.Collection;
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

    /** @param directory directory as Kodi sees it, with a trailing separator; also its subfolders count */
    public boolean liesBelow(String directory) {
        return KodiFiles.parts(file).stream()
                .anyMatch(part -> part.regionMatches(true, 0, directory, 0, directory.length()));
    }

    /**
     * @param files files as Kodi sees them
     * @return whether its file, for a stack one of its parts, is among them
     */
    public boolean isAmong(Collection<String> files) {
        return files.stream().anyMatch(candidate -> KodiFiles.matches(file, candidate));
    }
}
