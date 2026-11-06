package de.tiktak.kmm.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tiktak.kmm.nfo.StreamDetails;
import de.tiktak.kmm.scanner.DiscFormat;

/**
 * The tests with a real ffprobe only run if ffprobe and ffmpeg are on the PATH (always in CI).
 */
class FfprobeRunnerTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @TempDir
    Path directory;

    @Test
    void reportsAMissingProgram() {
        FfprobeRunner runner = new FfprobeRunner(directory.resolve("ffprobe-fehlt").toString(), TIMEOUT);

        assertThatThrownBy(() -> runner.run("film.mkv"))
                .isInstanceOfSatisfying(MediaProbeException.class,
                        e -> assertThat(e.localizedText().key()).isEqualTo("media.ffprobe.notStartable"));
    }

    @Test
    void reportsAFailingProgramWithItsErrorOutput() {
        String java = ProcessHandle.current().info().command().orElseThrow();

        assertThatThrownBy(() -> new FfprobeRunner(java, TIMEOUT).run("film.mkv"))
                .isInstanceOfSatisfying(MediaProbeException.class, e -> {
                    assertThat(e.localizedText().key()).isEqualTo("media.ffprobe.failed");
                    assertThat(e.localizedText().args()).containsEntry("input", "film.mkv");
                });
    }

    @Test
    void readsAVideoMadeByFfmpeg() throws IOException, InterruptedException {
        assumeTrue(available("ffprobe") && available("ffmpeg"), "ffprobe/ffmpeg not on PATH");
        Path video = directory.resolve("Test.mkv");
        Process ffmpeg = new ProcessBuilder("ffmpeg", "-v", "error", "-f", "lavfi", "-i",
                "testsrc=duration=2:size=320x180:rate=25", "-f", "lavfi", "-i", "sine=duration=2", "-c:v", "mpeg4",
                "-c:a", "aac", "-metadata:s:a:0", "language=ger", video.toString()).inheritIO().start();
        assertThat(ffmpeg.waitFor()).isZero();

        StreamDetails details = new MediaProbe(new FfprobeRunner("ffprobe", TIMEOUT)).probe(video, DiscFormat.FILE);

        assertThat(details.videos()).singleElement().satisfies(stream -> {
            assertThat(stream.codec()).isEqualTo("mpeg4");
            assertThat(stream.width()).isEqualTo(320);
            assertThat(stream.aspect()).isEqualTo(1.778);
            assertThat(stream.durationSeconds()).isEqualTo(2);
        });
        assertThat(details.audios()).singleElement().satisfies(stream -> {
            assertThat(stream.codec()).isEqualTo("aac");
            assertThat(stream.language()).isEqualTo("deu");
        });
    }

    @Test
    void stopsFfprobeAfterTheTimeout() {
        assumeTrue(available("ffprobe"), "ffprobe not on PATH");
        FfprobeRunner runner = new FfprobeRunner("ffprobe", Duration.ofMillis(500));

        assertThatThrownBy(() -> runner.run("pipe:0"))
                .isInstanceOfSatisfying(MediaProbeException.class,
                        e -> assertThat(e.localizedText().key()).isEqualTo("media.ffprobe.timedOut"));
    }

    private static boolean available(String program) {
        try {
            Process process = new ProcessBuilder(List.of(program, "-version")).start();
            process.getInputStream().transferTo(OutputStream.nullOutputStream());
            return process.waitFor() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
