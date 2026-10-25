package de.tiktak.kmm.movie;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Eine Seite der Filmliste. Eigene, stabile Darstellung statt der Spring-Data-internen {@code Page}.
 */
public record MoviePage(List<MovieSummary> content, int page, int size, long totalElements, int totalPages) {

    public MoviePage {
        content = List.copyOf(content);
    }

    static MoviePage from(Page<MovieSummary> page) {
        return new MoviePage(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }
}
