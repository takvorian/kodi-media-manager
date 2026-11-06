package de.tiktak.kmm.tvshow;

import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads the trailer links from the NFOs of the TV shows at startup that KMM took over before trailer support.
 * This way the TV show view and filter show existing trailers, and "Fetch missing trailers" overwrites none. The
 * NFOs are only read in the process.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TvShowTrailerBackfill {

    static final int BATCH_SIZE = 100;

    private final TvShowRepository showRepository;
    private final TvNfoSync nfoSync;
    private final TransactionTemplate transactions;

    @EventListener(ApplicationReadyEvent.class)
    void readUnknownTrailers() {
        List<Long> ids = showRepository.findIdsWithUnknownTrailer();
        if (ids.isEmpty()) {
            return;
        }
        for (int start = 0; start < ids.size(); start += BATCH_SIZE) {
            List<Long> batch = ids.subList(start, Math.min(start + BATCH_SIZE, ids.size()));
            transactions.executeWithoutResult(status -> showRepository.findAllById(batch)
                    .forEach(nfoSync::readTrailerIfUnknown));
        }
        log.info("Read the trailers of {} TV shows from their NFOs", ids.size());
    }
}
