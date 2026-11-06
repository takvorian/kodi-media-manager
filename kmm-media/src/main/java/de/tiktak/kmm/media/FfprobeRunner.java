package de.tiktak.kmm.media;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Starts the ffprobe program and reads only the details KMM needs for the stream details.
 */
public class FfprobeRunner implements ProbeRunner {

    static final String ENTRIES = "format=duration,bit_rate"
            + ":stream=codec_type,codec_name,codec_tag_string,profile,width,height,display_aspect_ratio,"
            + "sample_aspect_ratio,field_order,channels,bit_rate,color_transfer"
            + ":stream_tags=language,title,BPS,BPS-eng,stereo_mode"
            + ":stream_disposition=forced,attached_pic"
            + ":stream_side_data=side_data_type";
    private static final int MAX_ERROR_LENGTH = 500;

    private final String executable;
    private final Duration timeout;

    /**
     * @param executable path to ffprobe, or just {@code ffprobe} if it is on the PATH
     * @param timeout    maximum runtime per call; on network drives ffprobe needs seconds
     */
    public FfprobeRunner(String executable, Duration timeout) {
        this.executable = Objects.requireNonNull(executable, "executable must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
    }

    @Override
    public String run(String input) {
        Process process = start(input);
        try (var reader = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> read(process.getInputStream()),
                    reader);
            CompletableFuture<String> errors = CompletableFuture.supplyAsync(() -> read(process.getErrorStream()),
                    reader);
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw MediaProbeException.timedOut(input);
            }
            if (process.exitValue() != 0) {
                throw MediaProbeException.failed(input, abbreviated(errors.get().strip()));
            }
            return output.get();
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw MediaProbeException.timedOut(input);
        } catch (ExecutionException e) {
            throw MediaProbeException.failed(input, e.getCause().getMessage());
        }
    }

    private Process start(String input) {
        List<String> command = List.of(executable, "-v", "error", "-show_entries", ENTRIES, "-of", "json", input);
        try {
            return new ProcessBuilder(command).start();
        } catch (IOException e) {
            throw MediaProbeException.notStartable(executable, e);
        }
    }

    private static String read(InputStream stream) {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String abbreviated(String text) {
        return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH) + "…";
    }
}
