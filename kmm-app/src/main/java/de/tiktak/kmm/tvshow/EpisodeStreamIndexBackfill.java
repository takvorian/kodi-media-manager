package de.tiktak.kmm.tvshow;

import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates the queryable short form of the technical details at startup for episodes that do not have it yet, and
 * translates codec names along the way. New details get it immediately when saved.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class EpisodeStreamIndexBackfill {

    static final int BATCH_SIZE = 500;

    private final EpisodeRepository episodeRepository;
    private final TransactionTemplate transactions;

    @EventListener(ApplicationReadyEvent.class)
    void indexMissing() {
        List<Long> ids = episodeRepository.findIdsWithoutStreamIndex();
        if (ids.isEmpty()) {
            return;
        }
        for (int start = 0; start < ids.size(); start += BATCH_SIZE) {
            List<Long> batch = ids.subList(start, Math.min(start + BATCH_SIZE, ids.size()));
            transactions.executeWithoutResult(status -> episodeRepository.findAllById(batch)
                    .forEach(Episode::indexStreams));
        }
        log.info("Indexed the stream details of {} episodes for filters", ids.size());
    }
}
