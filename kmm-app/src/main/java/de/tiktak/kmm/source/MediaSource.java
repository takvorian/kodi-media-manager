package de.tiktak.kmm.source;

import java.time.Instant;
import java.util.Objects;

import de.tiktak.kmm.plugin.api.MediaType;
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
 * Ein Verzeichnis, in dem KMM nach Medien sucht (entspricht Embers „Quellen“).
 */
@Entity
@Table(name = "media_source")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, length = 1024)
    private String path;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 32)
    private MediaType mediaType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public MediaSource(String name, String path, MediaType mediaType) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.path = Objects.requireNonNull(path, "path must not be null");
        this.mediaType = Objects.requireNonNull(mediaType, "mediaType must not be null");
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
