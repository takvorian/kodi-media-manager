package de.tiktak.kmm.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import de.tiktak.kmm.plugin.api.MediaType;

class ScanDetailsTest {

    @Test
    void keepsEveryKindOfEntrySeparately() {
        ScanDetails.Builder builder = new ScanDetails.Builder();
        ScanEntry episode = new ScanEntry(MediaType.TV_EPISODE, 1L, "ALF – S01E01");
        ScanEntry show = new ScanEntry(MediaType.TV_SHOW, 2L, "ALF");
        builder.added(episode);
        builder.updated(episode);
        builder.removed(episode);
        builder.addedShow(show);
        builder.removedShow(show);
        builder.unreadableNfo("/media/alf/tvshow.nfo");

        ScanDetails details = builder.build();

        assertThat(details.added()).containsExactly(episode);
        assertThat(details.updated()).containsExactly(episode);
        assertThat(details.removed()).containsExactly(episode);
        assertThat(details.addedShows()).containsExactly(show);
        assertThat(details.removedShows()).containsExactly(show);
        assertThat(details.unreadableNfos()).containsExactly("/media/alf/tvshow.nfo");
    }

    @Test
    void limitsLongLists() {
        ScanDetails.Builder builder = new ScanDetails.Builder();

        IntStream.rangeClosed(0, ScanDetails.MAX_LISTED).forEach(index -> {
            builder.added(new ScanEntry(MediaType.MOVIE, (long) index, "Film " + index));
            builder.unreadableNfo("/media/" + index + ".nfo");
        });

        ScanDetails details = builder.build();
        assertThat(details.added()).hasSize(ScanDetails.MAX_LISTED);
        assertThat(details.unreadableNfos()).hasSize(ScanDetails.MAX_LISTED);
    }
}
