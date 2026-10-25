package de.tiktak.kmm.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import de.tiktak.kmm.plugin.api.MediaType;

@ExtendWith(MockitoExtension.class)
class MediaSourceServiceTest {

    @Mock
    private MediaSourceRepository repository;

    @InjectMocks
    private MediaSourceService service;

    @TempDir
    Path directory;

    @Test
    void createsSourceWithNormalizedAbsolutePathAndTrimmedName() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        String path = directory.resolve("sub").resolve("..").toString();

        MediaSource source = service.create(new CreateMediaSourceCommand("  Filme ", path, MediaType.MOVIE));

        assertThat(source.getName()).isEqualTo("Filme");
        assertThat(source.getPath()).isEqualTo(directory.toAbsolutePath().normalize().toString());
        assertThat(source.getMediaType()).isEqualTo(MediaType.MOVIE);
    }

    @Test
    void rejectsMissingDirectory() {
        String missing = directory.resolve("missing").toString();

        assertThatThrownBy(() -> service.create(new CreateMediaSourceCommand("Filme", missing, MediaType.MOVIE)))
                .isInstanceOfSatisfying(InvalidMediaSourcePathException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsFileInsteadOfDirectory() throws IOException {
        String file = Files.createFile(directory.resolve("movie.mkv")).toString();

        assertThatThrownBy(() -> service.create(new CreateMediaSourceCommand("Filme", file, MediaType.MOVIE)))
                .isInstanceOf(InvalidMediaSourcePathException.class);
    }

    @Test
    void rejectsSyntacticallyInvalidPath() {
        assertThatThrownBy(() -> service.create(new CreateMediaSourceCommand("Filme", "bad\u0000path", MediaType.MOVIE)))
                .isInstanceOf(InvalidMediaSourcePathException.class);
    }

    @Test
    void rejectsDuplicateName() {
        when(repository.existsByNameIgnoreCase("Filme")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new CreateMediaSourceCommand("Filme", directory.toString(), MediaType.MOVIE)))
                .isInstanceOfSatisfying(MediaSourceConflictException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getBody().getDetail()).contains("Filme");
                });
    }

    @Test
    void rejectsPathInsideExistingSource() throws IOException {
        Path nested = Files.createDirectory(directory.resolve("Kinder"));
        when(repository.findAll()).thenReturn(List.of(new MediaSource("Filme", directory.toString(), MediaType.MOVIE)));

        assertThatThrownBy(() -> service.create(
                new CreateMediaSourceCommand("Kinderfilme", nested.toString(), MediaType.MOVIE)))
                .isInstanceOfSatisfying(MediaSourceConflictException.class,
                        exception -> assertThat(exception.getBody().getDetail()).contains("überschneidet"));
    }

    @Test
    void rejectsPathContainingExistingSource() throws IOException {
        Path nested = Files.createDirectory(directory.resolve("Kinder"));
        when(repository.findAll()).thenReturn(List.of(new MediaSource("Kinder", nested.toString(), MediaType.MOVIE)));

        assertThatThrownBy(() -> service.create(
                new CreateMediaSourceCommand("Alles", directory.toString(), MediaType.MOVIE)))
                .isInstanceOf(MediaSourceConflictException.class);
    }

    @Test
    void acceptsSiblingDirectoryWithCommonPrefix() throws IOException {
        Path films = Files.createDirectory(directory.resolve("Filme"));
        Path filmsOld = Files.createDirectory(directory.resolve("FilmeAlt"));
        when(repository.findAll()).thenReturn(List.of(new MediaSource("Filme", films.toString(), MediaType.MOVIE)));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MediaSource created = service.create(new CreateMediaSourceCommand("Alt", filmsOld.toString(), MediaType.MOVIE));

        assertThat(created.getName()).isEqualTo("Alt");
    }

    @Test
    void listsSourcesSortedByName() {
        var source = new MediaSource("Filme", "/media/filme", MediaType.MOVIE);
        when(repository.findAll(any(Sort.class))).thenReturn(List.of(source));

        assertThat(service.findAll()).containsExactly(source);
    }

    @Test
    void deletesExistingSource() {
        var source = new MediaSource("Filme", "/media/filme", MediaType.MOVIE);
        when(repository.findById(1L)).thenReturn(Optional.of(source));

        service.delete(1L);

        verify(repository).delete(source);
    }

    @Test
    void rejectsDeletingUnknownSource() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOfSatisfying(MediaSourceNotFoundException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void marksSourceAsScanned() {
        var source = new MediaSource("Filme", "/media/filme", MediaType.MOVIE);
        when(repository.findById(1L)).thenReturn(Optional.of(source));
        Instant scannedAt = Instant.parse("2026-10-25T10:15:30Z");

        service.markScanned(1L, scannedAt);

        assertThat(source.getLastScannedAt()).isEqualTo(scannedAt);
    }

    @Test
    void importsSourceEvenIfItsDirectoryIsCurrentlyUnreachable() {
        when(repository.findAll()).thenReturn(List.of());
        when(repository.existsByNameIgnoreCase(any())).thenReturn(false);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        String unreachable = directory.resolve("netzlaufwerk").toString();

        ImportedSource imported = service.importSource(" Filme ", unreachable, MediaType.MOVIE);

        assertThat(imported.created()).isTrue();
        assertThat(imported.source().getName()).isEqualTo("Filme");
        assertThat(imported.source().getPath()).isEqualTo(unreachable);
    }

    @Test
    void reusesSourceWithTheSameDirectory() {
        MediaSource existing = new MediaSource("Meine Filme", directory.toString(), MediaType.MOVIE);
        when(repository.findAll()).thenReturn(List.of(existing));

        ImportedSource imported = service.importSource("filme", directory.resolve(".").toString(), MediaType.MOVIE);

        assertThat(imported.created()).isFalse();
        assertThat(imported.source()).isSameAs(existing);
        verify(repository, never()).save(any());
    }

    @Test
    void addsSuffixToTakenNameOnImport() {
        when(repository.findAll()).thenReturn(List.of());
        when(repository.existsByNameIgnoreCase("Filme")).thenReturn(true);
        when(repository.existsByNameIgnoreCase("Filme (2)")).thenReturn(true);
        when(repository.existsByNameIgnoreCase("Filme (3)")).thenReturn(false);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.importSource("Filme", directory.toString(), MediaType.MOVIE).source().getName())
                .isEqualTo("Filme (3)");
    }

    @Test
    void rejectsImportOfOverlappingSource() {
        when(repository.findAll()).thenReturn(List.of(new MediaSource("Alle", directory.toString(), MediaType.MOVIE)));
        String nested = directory.resolve("Filme").toString();

        assertThatThrownBy(() -> service.importSource("Filme", nested, MediaType.MOVIE))
                .isInstanceOf(MediaSourceConflictException.class);
    }
}
