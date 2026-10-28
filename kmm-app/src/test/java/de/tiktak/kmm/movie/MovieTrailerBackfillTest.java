package de.tiktak.kmm.movie;

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

import de.tiktak.kmm.scanner.DiscFormat;

@ExtendWith(MockitoExtension.class)
class MovieTrailerBackfillTest {

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private MovieNfoSync nfoSync;

    @Mock
    private TransactionTemplate transactions;

    @InjectMocks
    private MovieTrailerBackfill backfill;

    @Test
    void readsTheTrailersOfOlderMoviesInBatches() {
        List<Long> ids = LongStream.rangeClosed(1, MovieTrailerBackfill.BATCH_SIZE + 1).boxed().toList();
        Movie first = new Movie(1L, "/filme/Heat.mkv", "Heat", 1995, DiscFormat.FILE, 1);
        Movie last = new Movie(1L, "/filme/Ronin.mkv", "Ronin", 1998, DiscFormat.FILE, 1);
        when(movieRepository.findIdsWithUnknownTrailer()).thenReturn(ids);
        when(movieRepository.findAllById(ids.subList(0, MovieTrailerBackfill.BATCH_SIZE))).thenReturn(List.of(first));
        when(movieRepository.findAllById(ids.subList(MovieTrailerBackfill.BATCH_SIZE, ids.size())))
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
        when(movieRepository.findIdsWithUnknownTrailer()).thenReturn(List.of());

        backfill.readUnknownTrailers();

        verifyNoInteractions(transactions, nfoSync);
    }
}
