package de.tiktak.kmm.tvshow;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

interface TvShowRepository extends JpaRepository<TvShow, Long>, JpaSpecificationExecutor<TvShow> {

    List<TvShow> findBySourceId(long sourceId);

    List<TvShow> findByDirectoryPathHashIn(Collection<String> directoryPathHashes);

    /** Folder and modification time of the read {@code tvshow.nfo} per TV show of a source. */
    @Query("SELECT s.directoryPath, s.nfoModifiedAt FROM TvShow s WHERE s.sourceId = :sourceId")
    List<Object[]> findNfoTimestampsBySourceId(long sourceId);

    @Query("SELECT s.id FROM TvShow s WHERE s.trailerKnown = false")
    List<Long> findIdsWithUnknownTrailer();

    Page<TvShow> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    /** Number of TV shows per day. */
    @Query("SELECT t, COUNT(s) FROM TvShow s JOIN s.tags t GROUP BY t")
    List<Object[]> countByTag();

    @Query("SELECT s.id FROM TvShow s JOIN s.tags t WHERE t = :tag")
    List<Long> findIdsByTag(String tag);

    @Query("SELECT s.id, s.title FROM TvShow s JOIN s.tags t WHERE t = :tag ORDER BY s.title")
    List<Object[]> findTitlesByTag(String tag);

    @Query("SELECT DISTINCT g FROM TvShow s JOIN s.genres g")
    List<String> findDistinctGenres();

    @Query("SELECT DISTINCT t FROM TvShow s JOIN s.tags t")
    List<String> findDistinctTags();

    @Query("SELECT DISTINCT st FROM TvShow s JOIN s.studios st")
    List<String> findDistinctStudios();

    @Query("SELECT DISTINCT s.status FROM TvShow s WHERE s.status IS NOT NULL")
    List<String> findDistinctStatuses();

    @Query("SELECT DISTINCT s.mpaa FROM TvShow s WHERE s.mpaa IS NOT NULL")
    List<String> findDistinctMpaa();
}
