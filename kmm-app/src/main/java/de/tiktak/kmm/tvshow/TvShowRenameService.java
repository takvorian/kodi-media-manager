package de.tiktak.kmm.tvshow;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Benennt Serien, Staffelordner und Episodendateien nach Mustern um. Die Vorschau ändert nichts.
 */
@Service
@RequiredArgsConstructor
public class TvShowRenameService {

    private final TvShowRenamePlanning planning;

    /**
     * @return je Serie der Quelle, was das Umbenennen ändern würde, sortiert nach Ordner
     * @throws TvRenameProblemException wenn die Quelle keine Serien enthält oder ein Muster ungültig ist
     */
    public List<TvShowRenamePlan> preview(long sourceId, TvRenamePatterns patterns) {
        return planning.planSource(sourceId, patterns.parse()).plans();
    }

    /**
     * @throws TvRenameProblemException wenn ein Muster ungültig ist
     */
    public TvShowRenamePlan previewShow(long showId, TvRenamePatterns patterns) {
        return planning.planShow(showId, patterns.parse()).plans().getFirst();
    }
}
