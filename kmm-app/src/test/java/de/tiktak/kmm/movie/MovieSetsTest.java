package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MovieSetsTest {

    @Mock
    private MovieSetRepository repository;

    @InjectMocks
    private MovieSets movieSets;

    @Test
    void returnsKnownSetAndTakesOverItsOverview() {
        MovieSet matrix = new MovieSet("Matrix Collection");
        when(repository.findByName("Matrix Collection")).thenReturn(Optional.of(matrix));

        MovieSet resolved = movieSets.resolve(new MovieSupplement.SetReference("Matrix Collection", "Neo"));

        assertThat(resolved).isSameAs(matrix);
        assertThat(matrix.getOverview()).isEqualTo("Neo");
        verify(repository, never()).save(any());
    }

    @Test
    void keepsOverviewWhenReferenceHasNone() {
        MovieSet matrix = new MovieSet("Matrix Collection");
        matrix.describe("Neo");
        when(repository.findByName("Matrix Collection")).thenReturn(Optional.of(matrix));

        movieSets.resolve(new MovieSupplement.SetReference("Matrix Collection", null));

        assertThat(matrix.getOverview()).isEqualTo("Neo");
    }

    @Test
    void createsUnknownSet() {
        when(repository.findByName("Heat Collection")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MovieSet resolved = movieSets.resolve(new MovieSupplement.SetReference("Heat Collection", null));

        assertThat(resolved.getName()).isEqualTo("Heat Collection");
    }

    @Test
    void resolvesNoSetWithoutReference() {
        assertThat(movieSets.resolve(null)).isNull();
        verifyNoInteractions(repository);
    }
}
