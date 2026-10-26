package de.tiktak.kmm.tvshow;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface TvShowRepository extends JpaRepository<TvShow, Long> {

    List<TvShow> findBySourceId(long sourceId);

    Page<TvShow> findByTitleContainingIgnoreCase(String title, Pageable pageable);
}
