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
 * A movie set (a collection of related movies, e.g. a film series). Kodi assigns movies via the
 * name, which is why it is unique.
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

    /** Takes over the description if one is known; an existing one is not deleted. */
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

    /** Takes over edited details; an empty description and ID are deleted. */
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
