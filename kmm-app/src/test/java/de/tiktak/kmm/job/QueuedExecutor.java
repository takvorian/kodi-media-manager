package de.tiktak.kmm.job;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Executor für Tests: sammelt Aufgaben und führt sie erst auf Aufruf im Test-Thread aus. So lässt
 * sich der Zustand „Job läuft“ deterministisch prüfen.
 */
final class QueuedExecutor extends AbstractExecutorService {

    private final Queue<Runnable> queue = new ArrayDeque<>();

    @Override
    public void execute(Runnable command) {
        queue.add(command);
    }

    void runAll() {
        Runnable next;
        while ((next = queue.poll()) != null) {
            next.run();
        }
    }

    @Override
    public void shutdown() {
        queue.clear();
    }

    @Override
    public List<Runnable> shutdownNow() {
        List<Runnable> pending = List.copyOf(queue);
        queue.clear();
        return pending;
    }

    @Override
    public boolean isShutdown() {
        return false;
    }

    @Override
    public boolean isTerminated() {
        return queue.isEmpty();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return true;
    }
}
