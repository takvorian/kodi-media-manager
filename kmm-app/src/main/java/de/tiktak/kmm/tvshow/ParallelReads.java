package de.tiktak.kmm.tvshow;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

import de.tiktak.kmm.job.JobCancelledException;

/**
 * Führt Dateizugriffe mit begrenzter Parallelität auf virtuellen Threads aus. Auf Netzlaufwerken
 * bestimmt die Wartezeit je Zugriff die Dauer eines Scans, nicht die Rechenleistung.
 */
final class ParallelReads {

    private ParallelReads() {
    }

    /**
     * @return die Ergebnisse in der Reihenfolge der Eingaben
     * @throws RuntimeException die erste Ausnahme einer Aufgabe, in der Reihenfolge der Eingaben
     */
    static <T, R> List<R> map(List<T> items, int parallelism, String threadName, Function<T, R> task) {
        try (ExecutorService executor = Executors.newFixedThreadPool(parallelism,
                Thread.ofVirtual().name(threadName, 0).factory())) {
            List<Future<R>> futures = items.stream().map(item -> executor.submit(() -> task.apply(item))).toList();
            List<R> results = new ArrayList<>(futures.size());
            for (Future<R> future : futures) {
                results.add(await(future));
            }
            return results;
        }
    }

    private static <R> R await(Future<R> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JobCancelledException();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(e.getCause());
        }
    }
}
