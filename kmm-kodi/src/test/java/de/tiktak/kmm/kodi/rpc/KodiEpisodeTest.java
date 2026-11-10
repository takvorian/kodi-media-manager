package de.tiktak.kmm.kodi.rpc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KodiEpisodeTest {

    @Test
    void liesInTheDirectoryOfItsFileIgnoringCase() {
        KodiEpisode episode = new KodiEpisode(1, "smb://nas/serien/Lost/S2/Lost S02E01.mkv", 2, 1);

        assertThat(episode.liesIn("smb://nas/serien/Lost/s2/")).isTrue();
        assertThat(episode.liesIn("smb://nas/serien/Lost/")).as("only directly").isFalse();
    }

    @Test
    void liesInTheDirectoryOfAnyPartOfAStack() {
        KodiEpisode episode = new KodiEpisode(1, "stack://C:\\Lost\\S2\\a.mkv , C:\\Lost\\S2\\b.mkv", 2, 1);

        assertThat(episode.liesIn("C:\\Lost\\S2\\")).isTrue();
    }
}
