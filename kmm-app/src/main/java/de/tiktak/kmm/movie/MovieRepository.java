package de.tiktak.kmm.movie;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface MovieRepository extends JpaRepository<Movie, Long> {

    List<Movie> findBySourceId(long sourceId);

    /** Datei und Änderungszeit der eingelesenen NFO je Film einer Quelle. */
    @Query("SELECT m.filePath, m.nfoModifiedAt FROM Movie m WHERE m.sourceId = :sourceId")
    List<Object[]> findNfoTimestampsBySourceId(long sourceId);

    List<Movie> findByFilePathHashIn(Collection<String> filePathHashes);

    Page<Movie> findByTitleContainingIgnoreCase(String title, Pageable pageable);
}
