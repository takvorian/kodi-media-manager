package de.tiktak.kmm.tvshow;

/**
 * Which episodes of a TV show scraping covers: all, those of a season or those of one file (all episodes of a
 * multi-episode file, because they share one NFO).
 *
 * @param season   only the episodes of this season and of the TV show only its title; {@code null} for all
 * @param filePath only the episodes of this file, the TV show itself stays unchanged; {@code null} for all
 */
public record EpisodeScope(Integer season, String filePath) {

    private static final EpisodeScope ALL = new EpisodeScope(null, null);

    public static EpisodeScope all() {
        return ALL;
    }

    public static EpisodeScope season(int season) {
        return new EpisodeScope(season, null);
    }

    public static EpisodeScope file(String filePath) {
        return new EpisodeScope(null, filePath);
    }

    boolean covers(Episode episode) {
        return (season == null || episode.getSeason() == season)
                && (filePath == null || filePath.equals(episode.getFilePath()));
    }
}
