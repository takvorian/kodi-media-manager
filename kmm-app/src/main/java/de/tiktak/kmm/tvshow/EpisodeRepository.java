package de.tiktak.kmm.tvshow;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface EpisodeRepository extends JpaRepository<Episode, Long> {

    @Query("SELECT e.id FROM Episode e WHERE e.streamDetails IS NOT NULL AND e.streamIndex.audioCodecs IS NULL")
    List<Long> findIdsWithoutStreamIndex();

    @Query("SELECT DISTINCT e.videoSource FROM Episode e WHERE e.videoSource IS NOT NULL")
    List<String> findDistinctVideoSources();

    @Query("SELECT DISTINCT e.streamIndex.videoResolution FROM Episode e "
            + "WHERE e.streamIndex.videoResolution IS NOT NULL")
    List<Integer> findDistinctResolutions();

    @Query("SELECT DISTINCT e.streamIndex.videoCodec FROM Episode e WHERE e.streamIndex.videoCodec IS NOT NULL")
    List<String> findDistinctVideoCodecs();

    @Query("SELECT DISTINCT e.streamIndex.audioCodecs FROM Episode e WHERE e.streamIndex.audioCodecs IS NOT NULL")
    List<String> findDistinctAudioCodecLists();

    @Query("SELECT DISTINCT e.streamIndex.audioLanguages FROM Episode e "
            + "WHERE e.streamIndex.audioLanguages IS NOT NULL")
    List<String> findDistinctAudioLanguageLists();

    @Query("SELECT DISTINCT e.streamIndex.subtitleLanguages FROM Episode e "
            + "WHERE e.streamIndex.subtitleLanguages IS NOT NULL")
    List<String> findDistinctSubtitleLanguageLists();


    List<Episode> findByTvShowIdOrderBySeasonAscEpisodeAsc(long tvShowId);

    List<Episode> findByTvShowIdIn(Collection<Long> tvShowIds);

    List<Episode> findByFilePathHash(String filePathHash);

    /** File and modification time of the NFO read, per episode of a source. */
    @Query("SELECT e.filePath, e.nfoModifiedAt FROM Episode e "
            + "WHERE e.tvShowId IN (SELECT s.id FROM TvShow s WHERE s.sourceId = :sourceId)")
    List<Object[]> findNfoTimestampsBySourceId(long sourceId);

    /** Number of episodes per TV show as pairs {@code [tvShowId, count]}. */
    @Query("SELECT e.tvShowId, COUNT(e) FROM Episode e WHERE e.tvShowId IN :tvShowIds GROUP BY e.tvShowId")
    List<Object[]> countByTvShowIds(Collection<Long> tvShowIds);
}
