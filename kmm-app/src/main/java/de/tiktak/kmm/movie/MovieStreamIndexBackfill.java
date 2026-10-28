package de.tiktak.kmm.movie;

import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Legt beim Start die abfragbare Kurzform der technischen Angaben für Filme an, die sie noch nicht haben, etwa
 * nach dem Update auf eine Version mit Filtern. Neue Angaben bekommen sie sofort beim Speichern.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MovieStreamIndexBackfill {

    static final int BATCH_SIZE = 200;

    private final MovieRepository movieRepository;
    private final TransactionTemplate transactions;

    @EventListener(ApplicationReadyEvent.class)
    void indexMissing() {
        List<Long> ids = movieRepository.findIdsWithoutStreamIndex();
        if (ids.isEmpty()) {
            return;
        }
        for (int start = 0; start < ids.size(); start += BATCH_SIZE) {
            List<Long> batch = ids.subList(start, Math.min(start + BATCH_SIZE, ids.size()));
            transactions.executeWithoutResult(status -> movieRepository.findAllById(batch)
                    .forEach(Movie::indexStreams));
        }
        log.info("Indexed the stream details of {} movies for filters", ids.size());
    }
}
