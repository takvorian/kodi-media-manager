package de.tiktak.kmm.tvshow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class TvShowTrailerBackfillTest {

    @Mock
    private TvShowRepository showRepository;

    @Mock
    private TvNfoSync nfoSync;

    @Mock
    private TransactionTemplate transactions;

    @InjectMocks
    private TvShowTrailerBackfill backfill;

    @Test
    void readsTheTrailersOfOlderShowsInBatches() {
        List<Long> ids = LongStream.rangeClosed(1, TvShowTrailerBackfill.BATCH_SIZE + 1).boxed().toList();
        TvShow first = new TvShow(1L, "/serien/ALF", "ALF", 1986);
        TvShow last = new TvShow(1L, "/serien/Lost", "Lost", 2004);
        when(showRepository.findIdsWithUnknownTrailer()).thenReturn(ids);
        when(showRepository.findAllById(ids.subList(0, TvShowTrailerBackfill.BATCH_SIZE))).thenReturn(List.of(first));
        when(showRepository.findAllById(ids.subList(TvShowTrailerBackfill.BATCH_SIZE, ids.size())))
                .thenReturn(List.of(last));
        doAnswer(invocation -> {
            invocation.<Consumer<TransactionStatus>>getArgument(0).accept(null);
            return null;
        }).when(transactions).executeWithoutResult(any());

        backfill.readUnknownTrailers();

        verify(nfoSync).readTrailerIfUnknown(first);
        verify(nfoSync).readTrailerIfUnknown(last);
    }

    @Test
    void doesNothingWhenAllTrailersAreKnown() {
        when(showRepository.findIdsWithUnknownTrailer()).thenReturn(List.of());

        backfill.readUnknownTrailers();

        verifyNoInteractions(transactions, nfoSync);
    }
}
