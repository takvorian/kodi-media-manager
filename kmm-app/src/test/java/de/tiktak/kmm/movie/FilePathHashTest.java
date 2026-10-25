package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FilePathHashTest {

    private static final int SHA_256_HEX_LENGTH = 64;

    @Test
    void producesSha256HexDigest() {
        assertThat(FilePathHash.of("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void producesFixedLengthForLongPaths() {
        assertThat(FilePathHash.of("/media/" + "x".repeat(2000))).hasSize(SHA_256_HEX_LENGTH);
    }

    @Test
    void distinguishesDifferentPaths() {
        assertThat(FilePathHash.of("/media/a.mkv")).isNotEqualTo(FilePathHash.of("/media/b.mkv"));
    }
}
