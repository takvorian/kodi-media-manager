package de.tiktak.kmm.movie;

import java.util.Objects;

import org.hibernate.Length;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Ein Filmset (Sammlung zusammengehöriger Filme, z. B. eine Filmreihe). Kodi ordnet Filme über den
 * Namen zu, deshalb ist er eindeutig.
 */
@Entity
@Table(name = "movie_set")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class MovieSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = MovieSupplement.MAX_NAME_LENGTH)
    private String name;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "overview", length = Length.LONG32)
    private String overview;

    @Column(name = "tmdb_collection_id", length = 16)
    private String tmdbCollectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sort_method", nullable = false, length = 16)
    private SetSortMethod sortMethod = SetSortMethod.YEAR;

    MovieSet(String name) {
        this.name = Objects.requireNonNull(name, "name must not be null");
    }

    /** Übernimmt die Beschreibung, sofern eine bekannt ist; eine vorhandene wird nicht gelöscht. */
    void describe(String newOverview) {
        if (newOverview != null) {
            this.overview = newOverview;
        }
    }

    void identify(String newTmdbCollectionId) {
        if (newTmdbCollectionId != null) {
            this.tmdbCollectionId = newTmdbCollectionId;
        }
    }

    /** Übernimmt bearbeitete Angaben; leere Beschreibung und ID werden gelöscht. */
    void change(MovieSetData data) {
        this.name = data.name();
        this.overview = data.overview();
        this.tmdbCollectionId = data.tmdbCollectionId();
        this.sortMethod = data.sortMethod();
    }

    void sortBy(SetSortMethod newSortMethod) {
        this.sortMethod = Objects.requireNonNull(newSortMethod, "sortMethod must not be null");
    }

    MovieSupplement.SetReference toReference() {
        return new MovieSupplement.SetReference(name, overview, tmdbCollectionId);
    }
}
