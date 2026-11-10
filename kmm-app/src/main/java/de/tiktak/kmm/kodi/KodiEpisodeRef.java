package de.tiktak.kmm.kodi;

/**
 * An episode as Kodi finds it: by its file and, for multi-episode files, by season and number.
 *
 * @param filePath file as KMM sees it; mapped to Kodi's path per device
 */
public record KodiEpisodeRef(String filePath, int season, int episode) {
}
