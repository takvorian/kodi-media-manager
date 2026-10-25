package de.tiktak.kmm.movie;

import java.time.Instant;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import de.tiktak.kmm.scanner.DiscFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

    @Column(nullable = false, length = 512)
    private String title;

    @Column(name = "release_year")
    private Integer releaseYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "disc_format", nullable = false, length = 16)
    private DiscFormat discFormat;

    @Column(name = "part_count", nullable = false)
    private int partCount;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    Movie(long sourceId, String filePath, String title, Integer releaseYear, DiscFormat discFormat, int partCount) {
        this.sourceId = sourceId;
        this.filePath = Objects.requireNonNull(filePath, "filePath must not be null");
        this.filePathHash = FilePathHash.of(filePath);
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.releaseYear = releaseYear;
        this.discFormat = Objects.requireNonNull(discFormat, "discFormat must not be null");
        this.partCount = partCount;
    }

    @PrePersist
    void onCreate() {
        addedAt = Instant.now();
    }
}
