package de.tiktak.kmm.tvshow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class ParallelReadsTest {

    @Test
    void keepsTheOrderAndLimitsTheParallelism() {
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        List<Integer> numbers = IntStream.range(0, 50).boxed().toList();

        List<Integer> doubled = ParallelReads.map(numbers, 3, "test-", number -> {
            maximum.accumulateAndGet(running.incrementAndGet(), Math::max);
            sleepBriefly(number);
            running.decrementAndGet();
            return number * 2;
        });

        assertThat(doubled).containsExactlyElementsOf(numbers.stream().map(number -> number * 2).toList());
        assertThat(maximum.get()).isBetween(1, 3);
    }

    @Test
    void passesOnTheFailureOfATask() {
        var started = ConcurrentHashMap.<Integer>newKeySet();

        assertThatThrownBy(() -> ParallelReads.map(List.of(1, 2, 3), 2, "test-", number -> {
            started.add(number);
            if (number == 2) {
                throw new IllegalStateException("kaputt");
            }
            return number;
        })).isInstanceOf(IllegalStateException.class).hasMessage("kaputt");
        assertThat(started).containsExactlyInAnyOrder(1, 2, 3);
    }

    private static void sleepBriefly(int number) {
        try {
            Thread.sleep(number % 3);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
