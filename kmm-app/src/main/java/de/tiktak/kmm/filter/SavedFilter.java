package de.tiktak.kmm.filter;

import java.util.Objects;

import org.hibernate.Length;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A saved filter. The conditions are stored as JSON in {@link #definition}; their meaning is known by the
 * respective list (movies or TV shows).
 */
@Entity
@Table(name = "saved_filter")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class SavedFilter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 16)
    private FilterMediaType mediaType;

    @Column(nullable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false, length = Length.LONG32)
    private String definition;

    SavedFilter(FilterMediaType mediaType, String name, String definition) {
        this.mediaType = Objects.requireNonNull(mediaType, "mediaType must not be null");
        change(name, definition);
    }

    void change(String newName, String newDefinition) {
        this.name = Objects.requireNonNull(newName, "name must not be null");
        this.definition = Objects.requireNonNull(newDefinition, "definition must not be null");
    }
}
