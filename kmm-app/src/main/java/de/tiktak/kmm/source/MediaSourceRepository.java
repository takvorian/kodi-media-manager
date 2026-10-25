package de.tiktak.kmm.source;

import org.springframework.data.jpa.repository.JpaRepository;

interface MediaSourceRepository extends JpaRepository<MediaSource, Long> {

    boolean existsByNameIgnoreCase(String name);
}
