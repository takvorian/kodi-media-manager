package de.tiktak.kmm.source;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import de.tiktak.kmm.plugin.api.MediaType;

public interface MediaSourceRepository extends JpaRepository<MediaSource, Long> {

    Optional<MediaSource> findByName(String name);

    List<MediaSource> findByMediaTypeOrderByNameAsc(MediaType mediaType);
}
