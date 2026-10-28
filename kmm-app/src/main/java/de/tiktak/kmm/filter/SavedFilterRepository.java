package de.tiktak.kmm.filter;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SavedFilterRepository extends JpaRepository<SavedFilter, Long> {

    List<SavedFilter> findByMediaType(FilterMediaType mediaType);

    Optional<SavedFilter> findByMediaTypeAndNameIgnoreCase(FilterMediaType mediaType, String name);
}
