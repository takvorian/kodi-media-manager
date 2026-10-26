package de.tiktak.kmm.tvshow;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Eine Seite der Serienliste. Eigene, stabile Darstellung statt der Spring-Data-internen {@code Page}.
 */
public record TvShowPage(List<TvShowSummary> content, int page, int size, long totalElements, int totalPages) {

    public TvShowPage {
        content = List.copyOf(content);
    }

    static TvShowPage from(Page<TvShowSummary> page) {
        return new TvShowPage(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }
}
