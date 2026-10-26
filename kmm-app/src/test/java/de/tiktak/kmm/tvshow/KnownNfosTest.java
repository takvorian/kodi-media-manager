package de.tiktak.kmm.tvshow;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class KnownNfosTest {

    private static final Instant READ = Instant.parse("2026-10-26T10:00:00Z");
    private static final TvNfoState NFO = new TvNfoState(Path.of("x.nfo"), READ);

    @Test
    void knowsShowNfosReadWithTheSameModificationTime() {
        KnownNfos known = new KnownNfos(Map.of("ALF", READ, "Lost", Instant.EPOCH), Map.of());

        assertThat(known.isCurrentShowNfo("ALF", NFO)).isTrue();
        assertThat(known.isCurrentShowNfo("Lost", NFO)).isFalse();
        assertThat(known.isCurrentShowNfo("Neu", NFO)).isFalse();
    }

    @Test
    void knowsEpisodeNfosOnlyWhenEveryEpisodeOfTheFileReadThem() {
        KnownNfos known = new KnownNfos(Map.of(), Map.of(
                "eins.avi", Set.of(READ),
                "doppel.avi", Set.of(READ, Instant.EPOCH),
                "ohne.avi", new HashSet<>(Arrays.asList(READ, null))));

        assertThat(known.isCurrentEpisodeNfo("eins.avi", NFO)).isTrue();
        assertThat(known.isCurrentEpisodeNfo("doppel.avi", NFO)).isFalse();
        assertThat(known.isCurrentEpisodeNfo("ohne.avi", NFO)).isFalse();
        assertThat(known.isCurrentEpisodeNfo("neu.avi", NFO)).isFalse();
        assertThat(KnownNfos.NONE.isCurrentEpisodeNfo("eins.avi", NFO)).isFalse();
    }
}
