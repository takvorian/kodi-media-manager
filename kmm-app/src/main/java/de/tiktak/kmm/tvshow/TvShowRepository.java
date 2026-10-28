package de.tiktak.kmm.tvshow;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface TvShowRepository extends JpaRepository<TvShow, Long> {

    List<TvShow> findBySourceId(long sourceId);

    List<TvShow> findByDirectoryPathHashIn(Collection<String> directoryPathHashes);

    /** Ordner und Änderungszeit der eingelesenen {@code tvshow.nfo} je Serie einer Quelle. */
    @Query("SELECT s.directoryPath, s.nfoModifiedAt FROM TvShow s WHERE s.sourceId = :sourceId")
    List<Object[]> findNfoTimestampsBySourceId(long sourceId);

    Page<TvShow> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    /** Je Tag die Anzahl der Serien. */
    @Query("SELECT t, COUNT(s) FROM TvShow s JOIN s.tags t GROUP BY t")
    List<Object[]> countByTag();

    @Query("SELECT s.id FROM TvShow s JOIN s.tags t WHERE t = :tag")
    List<Long> findIdsByTag(String tag);

    @Query("SELECT s.id, s.title FROM TvShow s JOIN s.tags t WHERE t = :tag ORDER BY s.title")
    List<Object[]> findTitlesByTag(String tag);
}
