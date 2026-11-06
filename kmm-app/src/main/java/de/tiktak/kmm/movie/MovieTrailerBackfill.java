package de.tiktak.kmm.movie;

import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * At startup, reads the trailer links from the NFOs of movies KMM imported before trailer support.
 * This way the movie view and filters show existing trailers, and "Fetch missing trailers" overwrites none. The
 * NFOs are only read in the process.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MovieTrailerBackfill {

    static final int BATCH_SIZE = 100;

    private final MovieRepository movieRepository;
    private final MovieNfoSync nfoSync;
    private final TransactionTemplate transactions;

    @EventListener(ApplicationReadyEvent.class)
    void readUnknownTrailers() {
        List<Long> ids = movieRepository.findIdsWithUnknownTrailer();
        if (ids.isEmpty()) {
            return;
        }
        for (int start = 0; start < ids.size(); start += BATCH_SIZE) {
            List<Long> batch = ids.subList(start, Math.min(start + BATCH_SIZE, ids.size()));
            transactions.executeWithoutResult(status -> movieRepository.findAllById(batch)
                    .forEach(nfoSync::readTrailerIfUnknown));
        }
        log.info("Read the trailers of {} movies from their NFOs", ids.size());
    }
}
