package de.tiktak.kmm.tvshow;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

import org.hibernate.Length;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import de.tiktak.kmm.nfo.TvShowNfo;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A TV show of the library: a folder of a TV show source. The source is referenced only by its ID
 * so that the modules {@code tvshow} and {@code source} stay loosely coupled.
 */
@Entity
@Table(name = "tv_show")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TvShow {

    static final int MAX_TRAILER_LENGTH = 1024;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id", nullable = false, updatable = false)
    private Long sourceId;

    /** Only for sorting the list; computed on load. */
    @Formula("(select count(*) from episode e where e.tv_show_id = id)")
    private long episodeCount;

    @Column(name = "directory_path", nullable = false, length = 1024)
    private String directoryPath;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "directory_path_hash", nullable = false, length = 64)
    private String directoryPathHash;

    @Column(nullable = false, length = TvTexts.MAX_TITLE_LENGTH)
    private String title;

    @Column(name = "original_title", length = TvTexts.MAX_TITLE_LENGTH)
    private String originalTitle;

    @Column(name = "sort_title", length = TvTexts.MAX_TITLE_LENGTH)
    private String sortTitle;

    @Column(name = "release_year")
    private Integer releaseYear;

    @Column(name = "premiered")
    private LocalDate premiered;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "plot", length = Length.LONG32)
    private String plot;

    @Column(name = "runtime_minutes")
    private Integer runtime;

    @Column(name = "mpaa", length = TvTexts.MAX_SHORT_LENGTH)
    private String mpaa;

    @Column(name = "status", length = TvTexts.MAX_SHORT_LENGTH)
    private String status;

    @Column(name = "tvdb_id", length = 16)
    private String tvdbId;

    @Column(name = "tmdb_id", length = 16)
    private String tmdbId;

    @Column(name = "imdb_id", length = 16)
    private String imdbId;

    /**
     * Language when scraping, also for the episodes; without a value that of the source applies, otherwise that of
     * the settings.
     */
    @Column(name = "metadata_language", length = 16)
    private String metadataLanguage;

    @Column(name = "user_rating")
    private Integer userRating;

    /** Trailer link from the NFO, e.g. {@code plugin://plugin.video.youtube/...}, or {@code null}. */
    @Column(name = "trailer", length = MAX_TRAILER_LENGTH)
    private String trailer;

    /**
     * Whether {@link #trailer} reflects the state of the NFO. For TV shows from older versions it is unknown; the
     * entry in the NFO then stays unchanged when writing.
     */
    @Column(name = "trailer_known", nullable = false)
    private boolean trailerKnown;

    @ElementCollection
    @CollectionTable(name = "tv_show_genre", joinColumns = @JoinColumn(name = "tv_show_id"))
    @OrderColumn(name = "genre_order")
    @Column(name = "genre", nullable = false, length = TvTexts.MAX_GENRE_LENGTH)
    private List<String> genres = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "tv_show_studio", joinColumns = @JoinColumn(name = "tv_show_id"))
    @OrderColumn(name = "studio_order")
    @Column(name = "studio", nullable = false, length = TvTexts.MAX_LABEL_LENGTH)
    private List<String> studios = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "tv_show_tag", joinColumns = @JoinColumn(name = "tv_show_id"))
    @OrderColumn(name = "tag_order")
    @Column(name = "tag", nullable = false, length = TvTexts.MAX_LABEL_LENGTH)
    private List<String> tags = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "tv_show_actor", joinColumns = @JoinColumn(name = "tv_show_id"))
    @OrderColumn(name = "actor_order")
    private List<TvActorEntry> actors = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "tv_show_rating", joinColumns = @JoinColumn(name = "tv_show_id"))
    @OrderColumn(name = "rating_order")
    private List<TvRatingEntry> ratings = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "tv_season", joinColumns = @JoinColumn(name = "tv_show_id"))
    @OrderBy("number")
    private List<TvSeason> seasons = new ArrayList<>();

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    /** Modification time of the {@code tvshow.nfo} when last read or written; {@code null} without an NFO. */
    @Column(name = "nfo_modified_at")
    private Instant nfoModifiedAt;

    /**
     * Whether the details come from the {@code tvshow.nfo}. For TV shows from the Ember import they come from Ember's
     * database and may differ from the NFO; KMM then reads the NFO before writing it for the first time.
     */
    @Column(name = "nfo_read", nullable = false)
    private boolean nfoRead;

    TvShow(long sourceId, String directoryPath, String title, Integer releaseYear) {
        this.sourceId = sourceId;
        this.directoryPath = Objects.requireNonNull(directoryPath, "directoryPath must not be null");
        this.directoryPathHash = PathHash.of(directoryPath);
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.releaseYear = releaseYear;
    }

    public TvShowMetadata metadata() {
        return new TvShowMetadata(title, originalTitle, sortTitle, releaseYear, premiered, plot, runtime, mpaa,
                status, List.copyOf(genres), List.copyOf(studios), List.copyOf(tags), tvdbId, tmdbId, imdbId,
                userRating);
    }

    /**
     * Fills in a missing year and missing IMDb ID from the folder name so that scraping reliably finds the TV show. A
     * TV show that is already identified stays unchanged.
     */
    void fillMissingFromName(Integer nameYear, String nameImdbId) {
        if (tvdbId != null || tmdbId != null) {
            return;
        }
        if (releaseYear == null) {
            releaseYear = nameYear;
        }
        if (imdbId == null) {
            imdbId = nameImdbId;
        }
    }

    /** @return whether the language has changed */
    boolean changeMetadataLanguage(String language) {
        boolean changed = !Objects.equals(metadataLanguage, language);
        metadataLanguage = language;
        return changed;
    }

    void applyMetadata(TvShowMetadata metadata) {
        this.title = Objects.requireNonNull(metadata.title(), "title must not be null");
        this.originalTitle = metadata.originalTitle();
        this.sortTitle = metadata.sortTitle();
        this.releaseYear = yearOf(metadata);
        this.premiered = metadata.premiered();
        this.plot = metadata.plot();
        this.runtime = metadata.runtime();
        this.mpaa = metadata.mpaa();
        this.status = metadata.status();
        this.tvdbId = metadata.tvdbId();
        this.tmdbId = metadata.tmdbId();
        this.imdbId = metadata.imdbId();
        this.userRating = metadata.userRating();
        replace(genres, metadata.genres());
        replace(studios, metadata.studios());
        replace(tags, metadata.tags());
    }

    public TvShowExtras extras() {
        TreeMap<Integer, String> seasonNames = new TreeMap<>();
        seasons.stream().filter(season -> season.getTitle() != null)
                .forEach(season -> seasonNames.put(season.getNumber(), season.getTitle()));
        return new TvShowExtras(actors.stream().map(TvActorEntry::toActor).toList(),
                ratings.stream().map(TvRatingEntry::toRating).toList(), seasonNames);
    }

    void applyExtras(TvShowExtras extras) {
        actors.clear();
        extras.actors().forEach(actor -> actors.add(TvActorEntry.of(actor)));
        ratings.clear();
        extras.ratings().forEach(rating -> ratings.add(TvRatingEntry.of(rating)));
        seasons.clear();
        extras.seasonNames().forEach((number, name) -> seasons.add(new TvSeason(number, name)));
    }

    /** @param newTrailer link, or {@code null} or empty for "no trailer" */
    void changeTrailer(String newTrailer) {
        this.trailer = newTrailer == null || newTrailer.isBlank() ? null : newTrailer.strip();
        this.trailerKnown = true;
    }

    /** Trailer for the NFO: {@code null} if unknown, otherwise a link or "no trailer". */
    String nfoTrailer() {
        if (!trailerKnown) {
            return null;
        }
        return trailer == null ? TvShowNfo.NO_TRAILER : trailer;
    }

    void changeAddedAt(Instant newAddedAt) {
        this.addedAt = Objects.requireNonNull(newAddedAt, "addedAt must not be null");
    }

    void markNfoRead() {
        this.nfoRead = true;
    }

    /** The details do not come from the NFO, e.g. after an import. */
    void markNfoNotRead() {
        this.nfoRead = false;
    }

    void markNfoSynchronized(Instant modifiedAt) {
        this.nfoModifiedAt = Objects.requireNonNull(modifiedAt, "modifiedAt must not be null");
    }

    Path directory() {
        return Path.of(directoryPath);
    }

    /** After renaming: the TV show folder is now located there. */
    void relocate(String newDirectoryPath) {
        this.directoryPath = Objects.requireNonNull(newDirectoryPath, "newDirectoryPath must not be null");
        this.directoryPathHash = PathHash.of(newDirectoryPath);
    }

    /** Without a year and first air date, the year from the folder name is kept. */
    private Integer yearOf(TvShowMetadata metadata) {
        if (metadata.year() != null) {
            return metadata.year();
        }
        return metadata.premiered() != null ? Integer.valueOf(metadata.premiered().getYear()) : releaseYear;
    }

    private static void replace(List<String> target, List<String> values) {
        target.clear();
        target.addAll(values);
    }

    @PrePersist
    void onCreate() {
        if (addedAt == null) {
            addedAt = Instant.now();
        }
    }
}
