package de.tiktak.kmm.plugin.youtube;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

class ProcessCommandRunnerTest {

    private static final String JAVA = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    private final ProcessCommandRunner runner = new ProcessCommandRunner();

    @Test
    void runsAProgramAndCollectsItsOutput() throws IOException {
        CommandRunner.Result result = runner.run(List.of(JAVA, "-version"), Duration.ofSeconds(30));

        assertThat(result.succeeded()).isTrue();
        assertThat(result.errors()).containsIgnoringCase("version");
    }

    @Test
    void reportsFailuresMissingProgramsAndTimeouts() throws IOException {
        assertThat(runner.run(List.of(JAVA, "--unbekannte-option"), Duration.ofSeconds(30)).succeeded()).isFalse();
        assertThatThrownBy(() -> runner.run(List.of("kmm-gibt-es-nicht"), Duration.ofSeconds(5)))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> runner.run(List.of(JAVA, "-version"), Duration.ZERO))
                .isInstanceOf(IOException.class).hasMessageContaining("nicht geantwortet");
    }
}
