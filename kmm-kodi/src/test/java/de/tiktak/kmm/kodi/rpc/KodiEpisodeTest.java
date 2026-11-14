package de.tiktak.kmm.kodi.rpc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

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

    @Test
    void liesBelowTheFoldersAboveItsFile() {
        KodiEpisode episode = new KodiEpisode(1, "smb://nas/serien/Lost/S2/Lost S02E01.mkv", 2, 1);

        assertThat(episode.liesBelow("smb://nas/serien/lost/")).isTrue();
        assertThat(episode.liesBelow("smb://nas/serien/Lost/S2/")).isTrue();
        assertThat(episode.liesBelow("smb://nas/serien/Lost/S1/")).isFalse();
    }

    @Test
    void isAmongFilesWithAnyPartOfAStack() {
        KodiEpisode episode = new KodiEpisode(1, "stack://C:\\Lost\\S2\\a.mkv , C:\\Lost\\S2\\b.mkv", 2, 1);

        assertThat(episode.isAmong(List.of("C:\\Lost\\S2\\B.mkv"))).isTrue();
        assertThat(episode.isAmong(List.of("C:\\Lost\\S2\\c.mkv"))).isFalse();
    }
}
