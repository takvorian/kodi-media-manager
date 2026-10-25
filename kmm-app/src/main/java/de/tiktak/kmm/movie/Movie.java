package de.tiktak.kmm.movie;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.hibernate.Length;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import de.tiktak.kmm.scanner.CompanionFiles;
import de.tiktak.kmm.scanner.DiscFormat;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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

    @Column(name = "added_at", nullable = false, updatable = false)
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

    void markNfoSynchronized(Instant nfoModifiedAt) {
        this.nfoModifiedAt = Objects.requireNonNull(nfoModifiedAt, "nfoModifiedAt must not be null");
    }

    CompanionFiles companionFiles() {
        return CompanionFiles.of(Path.of(filePath), discFormat, partCount);
    }

    @PrePersist
    void onCreate() {
        addedAt = Instant.now();
    }
}
