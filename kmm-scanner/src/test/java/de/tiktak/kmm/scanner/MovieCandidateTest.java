package de.tiktak.kmm.scanner;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class MovieCandidateTest {

    private static final Path FILE = Path.of("Heat.mkv");

    @Test
    void rejectsMissingFile() {
        assertThatNullPointerException()
                .isThrownBy(() -> new MovieCandidate(null, "Heat", null, null, DiscFormat.FILE, 1))
                .withMessageContaining("file");
    }

    @Test
    void rejectsMissingTitle() {
        assertThatNullPointerException()
                .isThrownBy(() -> new MovieCandidate(FILE, null, null, null, DiscFormat.FILE, 1))
                .withMessageContaining("title");
    }

    @Test
    void rejectsMissingFormat() {
        assertThatNullPointerException()
                .isThrownBy(() -> new MovieCandidate(FILE, "Heat", null, null, null, 1))
                .withMessageContaining("format");
    }

    @Test
    void rejectsPartCountBelowOne() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MovieCandidate(FILE, "Heat", null, null, DiscFormat.FILE, 0))
                .withMessageContaining("partCount");
    }
}
