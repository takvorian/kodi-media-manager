package de.tiktak.kmm.tvshow;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface EpisodeRepository extends JpaRepository<Episode, Long> {

    List<Episode> findByTvShowIdOrderBySeasonAscEpisodeAsc(long tvShowId);

    List<Episode> findByTvShowIdIn(Collection<Long> tvShowIds);

    List<Episode> findByFilePathHash(String filePathHash);

    /** Anzahl der Episoden je Serie als Paare {@code [tvShowId, count]}. */
    @Query("SELECT e.tvShowId, COUNT(e) FROM Episode e WHERE e.tvShowId IN :tvShowIds GROUP BY e.tvShowId")
    List<Object[]> countByTvShowIds(Collection<Long> tvShowIds);
}
