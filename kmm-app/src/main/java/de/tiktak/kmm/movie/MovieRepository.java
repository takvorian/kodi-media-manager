package de.tiktak.kmm.movie;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

interface MovieRepository extends JpaRepository<Movie, Long>, JpaSpecificationExecutor<Movie> {

    List<Movie> findBySourceId(long sourceId);

    @Query("SELECT m.id FROM Movie m WHERE m.streamDetails IS NOT NULL AND m.streamIndex.audioCodecs IS NULL")
    List<Long> findIdsWithoutStreamIndex();

    @Query("SELECT m.id FROM Movie m WHERE m.trailerKnown = false")
    List<Long> findIdsWithUnknownTrailer();

    @Query("SELECT DISTINCT g FROM Movie m JOIN m.genres g")
    List<String> findDistinctGenres();

    @Query("SELECT DISTINCT t FROM Movie m JOIN m.tags t")
    List<String> findDistinctTags();

    @Query("SELECT DISTINCT c FROM Movie m JOIN m.countries c")
    List<String> findDistinctCountries();

    @Query("SELECT DISTINCT s FROM Movie m JOIN m.studios s")
    List<String> findDistinctStudios();

    @Query("SELECT DISTINCT m.mpaa FROM Movie m WHERE m.mpaa IS NOT NULL")
    List<String> findDistinctMpaa();

    @Query("SELECT DISTINCT m.edition FROM Movie m WHERE m.edition IS NOT NULL")
    List<String> findDistinctEditions();

    @Query("SELECT DISTINCT m.videoSource FROM Movie m WHERE m.videoSource IS NOT NULL")
    List<String> findDistinctVideoSources();

    @Query("SELECT DISTINCT m.streamIndex.videoResolution FROM Movie m WHERE m.streamIndex.videoResolution IS NOT NULL")
    List<Integer> findDistinctResolutions();

    @Query("SELECT DISTINCT m.streamIndex.videoCodec FROM Movie m WHERE m.streamIndex.videoCodec IS NOT NULL")
    List<String> findDistinctVideoCodecs();

    @Query("SELECT DISTINCT m.streamIndex.hdrType FROM Movie m WHERE m.streamIndex.hdrType IS NOT NULL")
    List<String> findDistinctHdrTypes();

    @Query("SELECT DISTINCT m.streamIndex.audioCodecs FROM Movie m WHERE m.streamIndex.audioCodecs IS NOT NULL")
    List<String> findDistinctAudioCodecLists();

    @Query("SELECT DISTINCT m.streamIndex.audioLanguages FROM Movie m WHERE m.streamIndex.audioLanguages IS NOT NULL")
    List<String> findDistinctAudioLanguageLists();

    @Query("SELECT DISTINCT m.streamIndex.subtitleLanguages FROM Movie m "
            + "WHERE m.streamIndex.subtitleLanguages IS NOT NULL")
    List<String> findDistinctSubtitleLanguageLists();

    /** Datei und Änderungszeit der eingelesenen NFO je Film einer Quelle. */
    @Query("SELECT m.filePath, m.nfoModifiedAt FROM Movie m WHERE m.sourceId = :sourceId")
    List<Object[]> findNfoTimestampsBySourceId(long sourceId);

    List<Movie> findByFilePathHashIn(Collection<String> filePathHashes);

    Page<Movie> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    @Query("SELECT m.id FROM Movie m WHERE m.movieSet.id = :setId")
    List<Long> findIdsByMovieSetId(long setId);

    @Query("SELECT m.id, m.title, m.releaseYear, m.sortTitle FROM Movie m WHERE m.movieSet.id = :setId")
    List<Object[]> findMembersByMovieSetId(long setId);

    /** Je Set die Anzahl der Filme. */
    @Query("SELECT m.movieSet.id, COUNT(m) FROM Movie m WHERE m.movieSet IS NOT NULL GROUP BY m.movieSet.id")
    List<Object[]> countByMovieSet();

    /** Je Tag die Anzahl der Filme. */
    @Query("SELECT t, COUNT(m) FROM Movie m JOIN m.tags t GROUP BY t")
    List<Object[]> countByTag();

    @Query("SELECT m.id FROM Movie m JOIN m.tags t WHERE t = :tag")
    List<Long> findIdsByTag(String tag);

    @Query("SELECT m.id, m.title FROM Movie m JOIN m.tags t WHERE t = :tag ORDER BY m.title")
    List<Object[]> findTitlesByTag(String tag);
}
