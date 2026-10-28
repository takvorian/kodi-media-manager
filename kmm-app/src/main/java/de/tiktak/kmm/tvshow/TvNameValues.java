package de.tiktak.kmm.tvshow;

import static de.tiktak.kmm.scanner.naming.NamePlaceholders.AIRED;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.AUDIO_CODEC;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.CHANNELS;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.EPISODE;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.GENRES;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.IMDB;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.MPAA;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.ORIGINAL_TITLE;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.RATING;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.RESOLUTION;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.SEASON;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.SHOW;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.SORT_TITLE;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.SOURCE;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.THREE_D;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.TITLE;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.TMDB;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.TVDB;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.VIDEO_CODEC;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.YEAR;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import de.tiktak.kmm.nfo.StreamDetails;
import de.tiktak.kmm.nfo.StreamLabels;

/**
 * Die Werte der Platzhalter einer Serie und, für Staffelordner und Episodendateien, der Episoden einer Datei.
 * Bei Mehrfachepisoden werden die Nummern mit „E“ verbunden ({@code S{season:2}E{episode:2}} ergibt
 * {@code S01E01E02}) und die Titel mit „ - “.
 *
 * @param episodes die Episoden einer Datei in Reihenfolge; leer für den Serienordner
 */
record TvNameValues(TvShowMetadata show, TvShowExtras showExtras, List<EpisodeValues> episodes) {

    private static final String LIST_SEPARATOR = ", ";
    private static final String EPISODE_SEPARATOR = "E";
    private static final String TITLE_SEPARATOR = " - ";

    TvNameValues {
        episodes = List.copyOf(episodes);
    }

    static TvNameValues of(TvShow show) {
        return new TvNameValues(show.metadata(), show.extras(), List.of());
    }

    TvNameValues withEpisodeValues(List<EpisodeValues> fileEpisodes) {
        return new TvNameValues(show, showExtras, fileEpisodes);
    }

    /** @return der Wert oder {@code null}, wenn er fehlt */
    String value(String placeholder) {
        return switch (placeholder) {
            case SHOW -> show.title();
            case ORIGINAL_TITLE -> show.originalTitle();
            case SORT_TITLE -> show.sortTitle() != null ? show.sortTitle() : show.title();
            case YEAR -> show.year() == null ? null : show.year().toString();
            case IMDB -> show.imdbId();
            case TMDB -> show.tmdbId();
            case TVDB -> show.tvdbId();
            case GENRES -> show.genres().isEmpty() ? null : String.join(LIST_SEPARATOR, show.genres());
            case MPAA -> show.mpaa();
            case RATING -> rating();
            case SEASON -> first().map(episode -> String.valueOf(episode.season())).orElse(null);
            case EPISODE -> episodes.isEmpty() ? null : episodes.stream()
                    .map(episode -> String.valueOf(episode.episode()))
                    .collect(Collectors.joining(EPISODE_SEPARATOR));
            case TITLE -> titles();
            case AIRED -> first().map(EpisodeValues::aired).orElse(null);
            case RESOLUTION -> video().flatMap(StreamLabels::resolution).orElse(null);
            case VIDEO_CODEC -> video().map(StreamDetails.Video::codec).orElse(null);
            case AUDIO_CODEC -> audio().map(StreamDetails.Audio::codec).orElse(null);
            case CHANNELS -> audio().map(StreamDetails.Audio::channels).map(String::valueOf).orElse(null);
            case SOURCE -> first().map(EpisodeValues::videoSource).orElse(null);
            case THREE_D -> video().flatMap(StreamLabels::threeD).orElse(null);
            default -> null;
        };
    }

    private String titles() {
        List<String> titles = episodes.stream().map(EpisodeValues::title).filter(Objects::nonNull).toList();
        return titles.isEmpty() ? null : String.join(TITLE_SEPARATOR, titles);
    }

    private String rating() {
        return showExtras.ratings().stream()
                .filter(TvRating::defaultRating)
                .findFirst()
                .or(() -> showExtras.ratings().stream().findFirst())
                .map(rating -> String.format(Locale.ROOT, "%.1f", rating.value()))
                .orElse(null);
    }

    private Optional<EpisodeValues> first() {
        return episodes.stream().findFirst();
    }

    private Optional<StreamDetails.Video> video() {
        return first().flatMap(episode -> episode.streams().videos().stream().findFirst());
    }

    private Optional<StreamDetails.Audio> audio() {
        return first().flatMap(episode -> episode.streams().audios().stream().findFirst());
    }

    /**
     * Was eine Episode zum Namen beiträgt.
     *
     * @param aired Erstausstrahlung als {@code JJJJ-MM-TT} oder {@code null}
     */
    record EpisodeValues(int season, int episode, String title, String aired, String videoSource,
                         StreamDetails streams) {

        static EpisodeValues of(Episode episode) {
            EpisodeMetadata metadata = episode.metadata();
            return new EpisodeValues(episode.getSeason(), episode.getEpisode(), metadata.title(),
                    metadata.aired() == null ? null : metadata.aired().toString(), metadata.videoSource(),
                    episode.streamDetails());
        }
    }
}
