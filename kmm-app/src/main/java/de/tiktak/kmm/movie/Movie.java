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

import de.tiktak.kmm.nfo.StreamDetails;
import de.tiktak.kmm.persistence.StreamDetailsConverter;
import de.tiktak.kmm.scanner.CompanionFiles;
import de.tiktak.kmm.scanner.DiscFormat;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
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
 * Ein Film der Bibliothek. Die Quelle wird nur über ihre ID referenziert, damit die Module
 * {@code movie} und {@code source} lose gekoppelt bleiben.
 */
@Entity
@Table(name = "movie")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id", nullable = false, updatable = false)
    private Long sourceId;

    @Column(name = "file_path", nullable = false, length = 1024, updatable = false)
    private String filePath;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "file_path_hash", nullable = false, length = 64, updatable = false)
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

    @Column(name = "runtime_minutes")
    private Integer runtime;

    @Column(name = "mpaa", length = MovieMetadata.MAX_MPAA_LENGTH)
    private String mpaa;

    @Column(name = "imdb_id", length = 16)
    private String imdbId;

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

    /** Position im Filmset oder {@code null}, wenn unbekannt. */
    @Column(name = "set_order")
    private Integer setOrder;

    @Column(name = "user_rating")
    private Integer userRating;

    @Column(name = "play_count")
    private Integer playCount;

    @Column(name = "last_played_at")
    private Instant lastPlayedAt;

    /**
     * Ob die weiteren Angaben (Darsteller, Bewertungen …) bekannt sind. Fehlen sie, weil die NFO
     * noch nicht neu eingelesen wurde, darf KMM sie beim Schreiben der NFO nicht löschen.
     */
    @Column(name = "supplement_known", nullable = false)
    private boolean supplementKnown;

    /** Video-, Ton- und Untertitelspuren aus NFO oder Migration. */
    @Getter(AccessLevel.NONE)
    @Convert(converter = StreamDetailsConverter.class)
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "stream_details", length = Length.LONG32)
    private StreamDetails streamDetails;

    /** Änderungszeit der NFO beim letzten Einlesen bzw. Schreiben; {@code null} ohne NFO. */
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
                runtime, mpaa, List.copyOf(genres), imdbId, tmdbId);
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
        this.runtime = metadata.runtime();
        this.mpaa = metadata.mpaa();
        this.imdbId = metadata.imdbId();
        this.tmdbId = metadata.tmdbId();
        this.genres.clear();
        this.genres.addAll(metadata.genres());
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
     * Übernimmt die weiteren Angaben.
     *
     * @param set das zum Verweis des Supplements gehörende Set oder {@code null}
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

    /** Ordnet den Film einem Set an der angegebenen Position zu. */
    void assignSet(MovieSet set, Integer order) {
        movieSet = set;
        setOrder = set == null ? null : order;
    }

    public StreamDetails streamDetails() {
        return streamDetails == null ? StreamDetails.EMPTY : streamDetails;
    }

    void applyStreamDetails(StreamDetails details) {
        this.streamDetails = details == null || details.isEmpty() ? null : details;
    }

    void changeAddedAt(Instant newAddedAt) {
        this.addedAt = Objects.requireNonNull(newAddedAt, "addedAt must not be null");
    }

    void changePlayback(Integer newPlayCount, Instant newLastPlayedAt) {
        this.playCount = newPlayCount;
        this.lastPlayedAt = newLastPlayedAt;
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
