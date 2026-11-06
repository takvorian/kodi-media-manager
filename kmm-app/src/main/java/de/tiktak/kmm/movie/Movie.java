package de.tiktak.kmm.movie;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.hibernate.Length;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import de.tiktak.kmm.nfo.CodecNames;
import de.tiktak.kmm.nfo.MovieNfoSupplement;
import de.tiktak.kmm.nfo.StreamDetails;
import de.tiktak.kmm.persistence.StreamDetailsConverter;
import de.tiktak.kmm.persistence.StreamIndex;
import de.tiktak.kmm.scanner.CompanionFiles;
import de.tiktak.kmm.scanner.DiscFormat;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A movie in the library. The source is referenced only by its ID so that the modules
 * {@code movie} and {@code source} stay loosely coupled.
 */
@Entity
@Table(name = "movie")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Movie {

    static final int MAX_TRAILER_LENGTH = 1024;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id", nullable = false, updatable = false)
    private Long sourceId;

    @Column(name = "file_path", nullable = false, length = 1024)
    private String filePath;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "file_path_hash", nullable = false, length = 64)
    private String filePathHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "disc_format", nullable = false, length = 16)
    private DiscFormat discFormat;

    @Column(name = "part_count", nullable = false)
    private int partCount;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    @Column(nullable = false, length = MovieMetadata.MAX_TITLE_LENGTH)
    private String title;

    @Column(name = "original_title", length = MovieMetadata.MAX_TITLE_LENGTH)
    private String originalTitle;

    @Column(name = "sort_title", length = MovieMetadata.MAX_TITLE_LENGTH)
    private String sortTitle;

    @Column(name = "release_year")
    private Integer releaseYear;

    @Column(name = "premiered")
    private LocalDate premiered;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "plot", length = Length.LONG32)
    private String plot;

    @Column(name = "outline", length = MovieMetadata.MAX_OUTLINE_LENGTH)
    private String outline;

    @Column(name = "tagline", length = MovieMetadata.MAX_TITLE_LENGTH)
    private String tagline;

    @Column(name = "edition", length = MovieMetadata.MAX_LABEL_LENGTH)
    private String edition;

    @Column(name = "video_source", length = MovieMetadata.MAX_LABEL_LENGTH)
    private String videoSource;

    @Column(name = "runtime_minutes")
    private Integer runtime;

    @Column(name = "mpaa", length = MovieMetadata.MAX_MPAA_LENGTH)
    private String mpaa;

    @Column(name = "imdb_id", length = 16)
    private String imdbId;

    /** Language when scraping; if unset, the source language applies, otherwise the one from the settings. */
    @Column(name = "metadata_language", length = 16)
    private String metadataLanguage;

    @Column(name = "tmdb_id", length = 16)
    private String tmdbId;

    @ElementCollection
    @CollectionTable(name = "movie_genre", joinColumns = @JoinColumn(name = "movie_id"))
    @OrderColumn(name = "genre_order")
    @Column(name = "genre", nullable = false, length = MovieMetadata.MAX_GENRE_LENGTH)
    private List<String> genres = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "movie_credit", joinColumns = @JoinColumn(name = "movie_id"))
    @OrderColumn(name = "credit_order")
    private List<MovieCredit> credits = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "movie_studio", joinColumns = @JoinColumn(name = "movie_id"))
    @OrderColumn(name = "studio_order")
    @Column(name = "studio", nullable = false, length = MovieSupplement.MAX_LABEL_LENGTH)
    private List<String> studios = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "movie_country", joinColumns = @JoinColumn(name = "movie_id"))
    @OrderColumn(name = "country_order")
    @Column(name = "country", nullable = false, length = MovieSupplement.MAX_LABEL_LENGTH)
    private List<String> countries = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "movie_tag", joinColumns = @JoinColumn(name = "movie_id"))
    @OrderColumn(name = "tag_order")
    @Column(name = "tag", nullable = false, length = MovieSupplement.MAX_LABEL_LENGTH)
    private List<String> tags = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "movie_rating", joinColumns = @JoinColumn(name = "movie_id"))
    @OrderColumn(name = "rating_order")
    private List<MovieRating> ratings = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_set_id")
    private MovieSet movieSet;

    /** Position in the movie set, or {@code null} if unknown. */
    @Column(name = "set_order")
    private Integer setOrder;

    @Column(name = "user_rating")
    private Integer userRating;

    @Column(name = "play_count")
    private Integer playCount;

    @Column(name = "last_played_at")
    private Instant lastPlayedAt;

    /**
     * Whether the further details (cast, ratings, ...) are known. If they are missing because the NFO
     * has not been re-read yet, KMM must not delete them when writing the NFO.
     */
    @Column(name = "supplement_known", nullable = false)
    private boolean supplementKnown;

    /**
     * Whether the details come from the NFO. For movies from the Ember import they come from Ember's database and
     * may
     * differ from the NFO; KMM then reads the NFO before the first write.
     */
    @Column(name = "nfo_read", nullable = false)
    private boolean nfoRead;

    /** Video, audio and subtitle tracks from the NFO or migration. */
    @Getter(AccessLevel.NONE)
    @Convert(converter = StreamDetailsConverter.class)
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "stream_details", length = Length.LONG32)
    private StreamDetails streamDetails;

    /** Trailer link from the NFO, such as {@code plugin://plugin.video.youtube/…}, or {@code null}. */
    @Column(name = "trailer", length = MAX_TRAILER_LENGTH)
    private String trailer;

    /**
     * Whether {@link #trailer} reflects the state of the NFO. For movies from older versions it is unknown; the
     * entry in the NFO then stays unchanged when writing.
     */
    @Column(name = "trailer_known", nullable = false)
    private boolean trailerKnown;

    /** Queryable short form of the technical details for filters; follows {@link #streamDetails}. */
    @Embedded
    private StreamIndex streamIndex;

    /** Modification time of the NFO when it was last read or written; {@code null} without an NFO. */
    @Column(name = "nfo_modified_at")
    private Instant nfoModifiedAt;

    Movie(long sourceId, String filePath, String title, Integer releaseYear, DiscFormat discFormat, int partCount) {
        this.sourceId = sourceId;
        this.filePath = Objects.requireNonNull(filePath, "filePath must not be null");
        this.filePathHash = FilePathHash.of(filePath);
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.releaseYear = releaseYear;
        this.discFormat = Objects.requireNonNull(discFormat, "discFormat must not be null");
        this.partCount = partCount;
    }

    public MovieMetadata metadata() {
        return new MovieMetadata(title, originalTitle, sortTitle, releaseYear, premiered, plot, outline, tagline,
                runtime, mpaa, List.copyOf(genres), imdbId, tmdbId, edition, videoSource);
    }

    void applyMetadata(MovieMetadata metadata) {
        this.title = Objects.requireNonNull(metadata.title(), "title must not be null");
        this.originalTitle = metadata.originalTitle();
        this.sortTitle = metadata.sortTitle();
        this.releaseYear = metadata.year();
        this.premiered = metadata.premiered();
        this.plot = metadata.plot();
        this.outline = metadata.outline();
        this.tagline = metadata.tagline();
        this.edition = metadata.edition();
        this.videoSource = metadata.videoSource();
        this.runtime = metadata.runtime();
        this.mpaa = metadata.mpaa();
        this.imdbId = metadata.imdbId();
        this.tmdbId = metadata.tmdbId();
        this.genres.clear();
        this.genres.addAll(metadata.genres());
    }

    /**
     * Fills in a missing year and a missing IMDb ID from the file or folder name so that scraping reliably
     * finds the movie. A movie that is already matched stays unchanged.
     *
     * @return whether anything changed
     */
    boolean fillMissingFromName(Integer nameYear, String nameImdbId) {
        if (tmdbId != null) {
            return false;
        }
        boolean fillYear = releaseYear == null && nameYear != null;
        boolean fillImdbId = imdbId == null && nameImdbId != null;
        if (fillYear) {
            releaseYear = nameYear;
        }
        if (fillImdbId) {
            imdbId = nameImdbId;
        }
        return fillYear || fillImdbId;
    }

    /**
     * Replaces a title that still comes from the file name with the one from the directory name. A title that
     * comes from
     * hand, an NFO, Ember or a provider stays.
     *
     * @return whether the title changed
     */
    boolean replaceFileTitle(String fileTitle, String directoryTitle) {
        if (tmdbId != null || !title.equals(fileTitle) || title.equals(directoryTitle)) {
            return false;
        }
        title = directoryTitle;
        return true;
    }

    /** @return whether the language changed */
    boolean changeMetadataLanguage(String language) {
        boolean changed = !Objects.equals(metadataLanguage, language);
        metadataLanguage = language;
        return changed;
    }

    public MovieSupplement supplement() {
        return new MovieSupplement(
                creditsOf(CreditType.ACTOR).map(MovieCredit::toActor).toList(),
                creditsOf(CreditType.DIRECTOR).map(MovieCredit::getName).toList(),
                creditsOf(CreditType.WRITER).map(MovieCredit::getName).toList(),
                List.copyOf(studios),
                List.copyOf(countries),
                List.copyOf(tags),
                ratings.stream().map(MovieRating::toRating).toList(),
                movieSet == null ? null : movieSet.toReference(),
                userRating,
                playCount,
                lastPlayedAt);
    }

    /**
     * Takes over the further details.
     *
     * @param set the set belonging to the supplement's reference, or {@code null}
     */
    void applySupplement(MovieSupplement supplement, MovieSet set) {
        credits.clear();
        supplement.actors().forEach(actor -> credits.add(MovieCredit.of(actor)));
        supplement.directors().forEach(name -> credits.add(MovieCredit.of(CreditType.DIRECTOR, name)));
        supplement.writers().forEach(name -> credits.add(MovieCredit.of(CreditType.WRITER, name)));
        replace(studios, supplement.studios());
        replace(countries, supplement.countries());
        replace(tags, supplement.tags());
        ratings.clear();
        supplement.ratings().forEach(rating -> ratings.add(MovieRating.of(rating)));
        if (set != movieSet) {
            movieSet = set;
            setOrder = null;
        }
        userRating = supplement.userRating();
        playCount = supplement.playCount();
        lastPlayedAt = supplement.lastPlayed();
        supplementKnown = true;
    }

    /** Assigns the movie to a set at the given position. */
    void assignSet(MovieSet set, Integer order) {
        movieSet = set;
        setOrder = set == null ? null : order;
    }

    public StreamDetails streamDetails() {
        return streamDetails == null ? StreamDetails.EMPTY : streamDetails;
    }

    void applyStreamDetails(StreamDetails details) {
        this.streamDetails = details == null || details.isEmpty() ? null : CodecNames.normalize(details);
        this.streamIndex = StreamIndex.of(this.streamDetails);
    }

    /**
     * Creates the short form for movies whose technical details were saved before the short form existed, and
     * translates
     * codec names that were taken over raw from Ember, for example.
     */
    void indexStreams() {
        applyStreamDetails(this.streamDetails);
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
        return trailer == null ? MovieNfoSupplement.NO_TRAILER : trailer;
    }

    void changeAddedAt(Instant newAddedAt) {
        this.addedAt = Objects.requireNonNull(newAddedAt, "addedAt must not be null");
    }

    void changePlayback(Integer newPlayCount, Instant newLastPlayedAt, Integer newUserRating) {
        this.playCount = newPlayCount;
        this.lastPlayedAt = newLastPlayedAt;
        this.userRating = newUserRating;
    }

    /** After renaming: the main file is now located there. */
    void relocate(String newFilePath) {
        this.filePath = Objects.requireNonNull(newFilePath, "newFilePath must not be null");
        this.filePathHash = FilePathHash.of(newFilePath);
    }

    void markNfoRead() {
        this.nfoRead = true;
    }

    /** The details do not come from the NFO, for example after an import. */
    void markNfoNotRead() {
        this.nfoRead = false;
    }

    void markNfoSynchronized(Instant nfoModifiedAt) {
        this.nfoModifiedAt = Objects.requireNonNull(nfoModifiedAt, "nfoModifiedAt must not be null");
    }

    CompanionFiles companionFiles() {
        return CompanionFiles.of(Path.of(filePath), discFormat, partCount);
    }

    private Stream<MovieCredit> creditsOf(CreditType type) {
        return credits.stream().filter(credit -> credit.getType() == type);
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
