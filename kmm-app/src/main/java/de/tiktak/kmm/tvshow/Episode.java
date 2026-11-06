package de.tiktak.kmm.tvshow;

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
import de.tiktak.kmm.nfo.StreamDetails;
import de.tiktak.kmm.persistence.StreamDetailsConverter;
import de.tiktak.kmm.persistence.StreamIndex;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
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
 * An episode of a TV show. A file with a multi-episode ({@code S01E01E02}) yields several
 * episodes with the same file, as in Kodi.
 */
@Entity
@Table(name = "episode")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Episode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tv_show_id", nullable = false, updatable = false)
    private Long tvShowId;

    @Column(name = "file_path", nullable = false, length = 1024)
    private String filePath;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "file_path_hash", nullable = false, length = 64)
    private String filePathHash;

    @Column(name = "season_number", nullable = false, updatable = false)
    private int season;

    @Column(name = "episode_number", nullable = false, updatable = false)
    private int episode;

    @Column(nullable = false, length = TvTexts.MAX_TITLE_LENGTH)
    private String title;

    @Column(name = "original_title", length = TvTexts.MAX_TITLE_LENGTH)
    private String originalTitle;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "plot", length = Length.LONG32)
    private String plot;

    @Column(name = "aired")
    private LocalDate aired;

    @Column(name = "runtime_minutes")
    private Integer runtime;

    @Column(name = "tvdb_id", length = 16)
    private String tvdbId;

    @Column(name = "tmdb_id", length = 16)
    private String tmdbId;

    @Column(name = "imdb_id", length = 16)
    private String imdbId;

    @Column(name = "user_rating")
    private Integer userRating;

    @Column(name = "video_source", length = TvTexts.MAX_LABEL_LENGTH)
    private String videoSource;

    @Column(name = "play_count")
    private Integer playCount;

    @Column(name = "last_played_at")
    private Instant lastPlayedAt;

    @ElementCollection
    @CollectionTable(name = "episode_credit", joinColumns = @JoinColumn(name = "episode_id"))
    @OrderColumn(name = "credit_order")
    private List<EpisodeCredit> credits = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "episode_rating", joinColumns = @JoinColumn(name = "episode_id"))
    @OrderColumn(name = "rating_order")
    private List<TvRatingEntry> ratings = new ArrayList<>();

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    @Getter(AccessLevel.NONE)
    @Convert(converter = StreamDetailsConverter.class)
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "stream_details", length = Length.LONG32)
    private StreamDetails streamDetails;

    /** Queryable short form of the technical details for filters; follows {@link #streamDetails}. */
    @Embedded
    private StreamIndex streamIndex;

    /** Modification time of the NFO when last read or written; {@code null} without an NFO. */
    @Column(name = "nfo_modified_at")
    private Instant nfoModifiedAt;

    /**
     * Whether the details come from the NFO. For episodes from the Ember import they come from Ember's database and
     * may differ from the NFO; KMM then reads the NFO before rewriting it for the first time.
     */
    @Column(name = "nfo_read", nullable = false)
    private boolean nfoRead;

    Episode(long tvShowId, String filePath, int season, int episode, String title) {
        this.tvShowId = tvShowId;
        this.filePath = Objects.requireNonNull(filePath, "filePath must not be null");
        this.filePathHash = PathHash.of(filePath);
        this.season = season;
        this.episode = episode;
        this.title = Objects.requireNonNull(title, "title must not be null");
    }

    public EpisodeMetadata metadata() {
        return new EpisodeMetadata(title, originalTitle, plot, aired, runtime, tvdbId, tmdbId, imdbId, userRating,
                videoSource);
    }

    void applyMetadata(EpisodeMetadata metadata) {
        this.title = Objects.requireNonNull(metadata.title(), "title must not be null");
        this.originalTitle = metadata.originalTitle();
        this.plot = metadata.plot();
        this.aired = metadata.aired();
        this.runtime = metadata.runtime();
        this.tvdbId = metadata.tvdbId();
        this.tmdbId = metadata.tmdbId();
        this.imdbId = metadata.imdbId();
        this.userRating = metadata.userRating();
        this.videoSource = metadata.videoSource();
    }

    public EpisodeExtras extras() {
        return new EpisodeExtras(
                creditsOf(EpisodeCredit.Type.DIRECTOR).map(EpisodeCredit::getName).toList(),
                creditsOf(EpisodeCredit.Type.WRITER).map(EpisodeCredit::getName).toList(),
                creditsOf(EpisodeCredit.Type.ACTOR).map(EpisodeCredit::toActor).toList(),
                ratings.stream().map(TvRatingEntry::toRating).toList(),
                playCount,
                lastPlayedAt,
                streamDetails == null ? StreamDetails.EMPTY : streamDetails);
    }

    void applyExtras(EpisodeExtras extras) {
        credits.clear();
        extras.actors().forEach(actor -> credits.add(EpisodeCredit.actor(actor)));
        extras.directors().forEach(name -> credits.add(EpisodeCredit.of(EpisodeCredit.Type.DIRECTOR, name)));
        extras.writers().forEach(name -> credits.add(EpisodeCredit.of(EpisodeCredit.Type.WRITER, name)));
        ratings.clear();
        extras.ratings().forEach(rating -> ratings.add(TvRatingEntry.of(rating)));
        playCount = extras.playCount();
        lastPlayedAt = extras.lastPlayed();
        applyStreamDetails(extras.streams());
    }

    void changeAddedAt(Instant newAddedAt) {
        this.addedAt = Objects.requireNonNull(newAddedAt, "addedAt must not be null");
    }

    boolean hasStreamDetails() {
        return streamDetails != null && !streamDetails.isEmpty();
    }

    StreamDetails streamDetails() {
        return streamDetails == null ? StreamDetails.EMPTY : streamDetails;
    }

    void applyStreamDetails(StreamDetails details) {
        streamDetails = details == null || details.isEmpty() ? null : CodecNames.normalize(details);
        streamIndex = StreamIndex.of(streamDetails);
    }

    /** Creates the short form and translates codec names along the way, e.g. for episodes from older versions. */
    void indexStreams() {
        applyStreamDetails(streamDetails);
    }

    void changePlayback(Integer newPlayCount, Instant newLastPlayedAt, Integer newUserRating) {
        this.playCount = newPlayCount;
        this.lastPlayedAt = newLastPlayedAt;
        this.userRating = newUserRating;
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

    Path file() {
        return Path.of(filePath);
    }

    /** After renaming: the file is now located there. */
    void relocate(String newFilePath) {
        this.filePath = Objects.requireNonNull(newFilePath, "newFilePath must not be null");
        this.filePathHash = PathHash.of(newFilePath);
    }

    private Stream<EpisodeCredit> creditsOf(EpisodeCredit.Type type) {
        return credits.stream().filter(credit -> credit.getType() == type);
    }

    @PrePersist
    void onCreate() {
        if (addedAt == null) {
            addedAt = Instant.now();
        }
    }
}
