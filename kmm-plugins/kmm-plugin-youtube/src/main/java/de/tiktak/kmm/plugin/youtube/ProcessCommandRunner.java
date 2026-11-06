package de.tiktak.kmm.plugin.youtube;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Starts programs as a process of their own. Outputs are read alongside so full buffers do not stop the process; it
 * is ended when the time is up.
 */
final class ProcessCommandRunner implements CommandRunner {

    @Override
    public Result run(List<String> command, Duration timeout) throws IOException {
        Process process = new ProcessBuilder(command).start();
        process.getOutputStream().close();
        CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> read(process.getInputStream()));
        CompletableFuture<String> errors = CompletableFuture.supplyAsync(() -> read(process.getErrorStream()));
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IOException("%s did not respond within %d seconds".formatted(command.getFirst(),
                        timeout.toSeconds()));
            }
            return new Result(process.exitValue(), output.join(), errors.join());
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    private static String read(InputStream stream) {
        try (InputStream input = stream; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            input.transferTo(bytes);
            return bytes.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
