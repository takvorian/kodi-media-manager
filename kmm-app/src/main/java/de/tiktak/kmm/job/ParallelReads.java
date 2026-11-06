package de.tiktak.kmm.job;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * Runs file accesses with limited parallelism on virtual threads. On network drives
 * the wait time per access determines the duration of a scan, not the computing power.
 */
public final class ParallelReads {

    private ParallelReads() {
    }

    /**
     * @return the results in the order of the inputs
     * @throws RuntimeException the first exception of a task, in the order of the inputs
     */
    public static <T, R> List<R> map(List<T> items, int parallelism, String threadName, Function<T, R> task) {
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
