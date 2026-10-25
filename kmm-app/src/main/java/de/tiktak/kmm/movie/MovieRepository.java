package de.tiktak.kmm.movie;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface MovieRepository extends JpaRepository<Movie, Long> {

    List<Movie> findBySourceId(long sourceId);

    List<Movie> findByFilePathHashIn(Collection<String> filePathHashes);

    Page<Movie> findByTitleContainingIgnoreCase(String title, Pageable pageable);
}
