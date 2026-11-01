package de.tiktak.kmm.kodi.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class KodiLibrarySchedulerTest {

    private static final String DIRECTORY = "smb://nas/filme/Heat/";
    private static final String IS_SCANNING = "XBMC.GetInfoBooleans";
    private static final String BUSY = "{\"Library.IsScanningVideo\": true}";
    private static final String IDLE = "{\"Library.IsScanningVideo\": false}";
    private static final Duration LONG = Duration.ofSeconds(10);

    private final FakeKodiServer kodi = new FakeKodiServer();
    private final KodiLibraryScheduler scheduler = new KodiLibraryScheduler(
            new KodiClient(HttpClient.newHttpClient(), Duration.ofSeconds(5)), Duration.ofMillis(5));

    @AfterEach
    void stopKodi() {
        kodi.close();
    }

    @Test
    void startsRightAwayWhenKodiIsIdle() {
        scheduler.scan(kodi.endpoint(), DIRECTORY, Duration.ZERO);

        assertThat(kodi.methods()).containsExactly(IS_SCANNING, "VideoLibrary.Scan");
    }

    @Test
    void waitsForTheRunningScanInsteadOfCancellingIt() throws Exception {
        kodi.respond(IS_SCANNING, BUSY);

        CompletableFuture<Void> scan = CompletableFuture.runAsync(() -> scheduler.scan(kodi.endpoint(), DIRECTORY, LONG));

        awaitRepeatedQuestions();
        assertThat(kodi.methods()).doesNotContain("VideoLibrary.Scan");
        kodi.respond(IS_SCANNING, IDLE);
        scan.get(5, TimeUnit.SECONDS);
        assertThat(kodi.methods()).endsWith("VideoLibrary.Scan");
    }

    @Test
    void cleansOnlyAfterTheScanIsFinished() throws Exception {
        kodi.respond(IS_SCANNING, BUSY);

        CompletableFuture<Void> clean = CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> scheduler.cleanMovies(kodi.endpoint(), DIRECTORY, LONG)),
                CompletableFuture.runAsync(() -> scheduler.cleanTvShows(kodi.endpoint(), DIRECTORY, LONG)));

        awaitRepeatedQuestions();
        assertThat(kodi.methods()).doesNotContain("VideoLibrary.Clean");
        kodi.respond(IS_SCANNING, IDLE);
        clean.get(5, TimeUnit.SECONDS);
        assertThat(kodi.calls()).filteredOn(call -> call.method().equals("VideoLibrary.Clean"))
                .extracting(call -> call.params().path("content").asString())
                .containsExactlyInAnyOrder("movies", "tvshows");
    }

    @Test
    void givesUpWhenKodiStaysBusy() {
        kodi.respond(IS_SCANNING, BUSY);

        assertThatThrownBy(() -> scheduler.scan(kodi.endpoint(), DIRECTORY, Duration.ofMillis(20)))
                .isInstanceOf(KodiException.class)
                .hasMessage("Kodi liest gerade seine Bibliothek ein. Versuche es danach noch einmal.");
        assertThat(kodi.methods()).containsOnly(IS_SCANNING);
    }

    @Test
    void stopsWaitingWhenInterrupted() throws Exception {
        kodi.respond(IS_SCANNING, BUSY);
        CompletableFuture<Throwable> failure = new CompletableFuture<>();
        Thread waiting = Thread.ofVirtual().start(() -> {
            try {
                scheduler.scan(kodi.endpoint(), DIRECTORY, LONG);
            } catch (KodiException e) {
                failure.complete(e);
            }
        });

        awaitRepeatedQuestions();
        waiting.interrupt();

        // Die Unterbrechung trifft die Pause oder eine gerade laufende Nachfrage; beide beenden das Warten.
        assertThat(failure.get(5, TimeUnit.SECONDS)).isInstanceOf(KodiException.class);
        waiting.join(Duration.ofSeconds(5));
        assertThat(waiting.isAlive()).isFalse();
    }

    @Test
    void reportsAnUnreachableKodi() {
        kodi.fail(IS_SCANNING, -32601, "Method not found.");

        assertThatThrownBy(() -> scheduler.scan(kodi.endpoint(), DIRECTORY, LONG))
                .isInstanceOf(KodiException.class);
        assertThat(kodi.methods()).doesNotContain("VideoLibrary.Scan");
    }

    private void awaitRepeatedQuestions() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture.runAsync(() -> {
            while (kodi.methods().stream().filter(IS_SCANNING::equals).count() < 3) {
                Thread.onSpinWait();
            }
        }).get(5, TimeUnit.SECONDS);
    }
}
