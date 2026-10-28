package de.tiktak.kmm.artwork;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tiktak.kmm.plugin.api.ImageType;
import de.tiktak.kmm.settings.MovieSetSettings;

class MovieSetArtworkFilesTest {

    private static final byte[] JPEG = {1, 2, 3};
    private static final String TITLE = "Star Wars: Filmreihe";

    private final MovieSetArtworkFiles files = new MovieSetArtworkFiles(new ArtworkFiles());

    @TempDir
    Path directory;

    private Path flat;
    private Path kodi;
    private MovieSetSettings both;

    @BeforeEach
    void setUp() {
        flat = directory.resolve("_filmsets");
        kodi = directory.resolve("sets");
        both = new MovieSetSettings(true, flat.toString(), true, kodi.toString(), false);
    }

    @Test
    void savesIntoEveryEnabledStorageAndCreatesTheFolders() {
        ArtworkFile saved = files.save(both, TITLE, ImageType.POSTER, new DownloadedImage(JPEG, "jpg"));

        assertThat(saved.file()).isEqualTo(flat.resolve("Star Wars- Filmreihe-poster.jpg"));
        assertThat(flat.resolve("Star Wars- Filmreihe-poster.jpg")).hasBinaryContent(JPEG);
        assertThat(kodi.resolve("Star Wars_ Filmreihe").resolve("poster.jpg")).hasBinaryContent(JPEG);
    }

    @Test
    void writesOnlyIntoEnabledStorages() {
        var kodiOnly = new MovieSetSettings(false, flat.toString(), true, kodi.toString(), false);

        files.save(kodiOnly, TITLE, ImageType.CLEARLOGO, new DownloadedImage(JPEG, "png"));

        assertThat(flat).doesNotExist();
        assertThat(kodi.resolve("Star Wars_ Filmreihe").resolve("clearlogo.png")).exists();
    }

    @Test
    void findsTheImageInTheFirstStorageThatHasOne() throws IOException {
        Path folder = Files.createDirectories(kodi.resolve("Star Wars_ Filmreihe"));
        Files.write(folder.resolve("fanart.png"), JPEG);

        assertThat(files.find(both, TITLE, ImageType.FANART)).get()
                .extracting(ArtworkFile::file).isEqualTo(folder.resolve("fanart.png"));
        assertThat(files.find(both, TITLE, ImageType.POSTER)).isEmpty();
        assertThat(files.find(MovieSetSettings.DEFAULTS, TITLE, ImageType.FANART)).isEmpty();
    }

    @Test
    void deletesFromEveryStorage() {
        files.save(both, TITLE, ImageType.POSTER, new DownloadedImage(JPEG, "jpg"));

        files.delete(both, TITLE, ImageType.POSTER);

        assertThat(files.find(both, TITLE, ImageType.POSTER)).isEmpty();
    }

    @Test
    void movesTheImagesToTheNewTitleAndRemovesTheEmptySetFolder() {
        files.save(both, "Matrix", ImageType.POSTER, new DownloadedImage(JPEG, "jpg"));
        files.save(both, "Matrix", ImageType.DISCART, new DownloadedImage(JPEG, "png"));

        files.rename(both, "Matrix", "Matrix Filmreihe", ArtworkTypes.MOVIE_SET_TYPES);

        assertThat(flat.resolve("Matrix Filmreihe-poster.jpg")).exists();
        assertThat(flat.resolve("Matrix Filmreihe-discart.png")).exists();
        assertThat(kodi.resolve("Matrix Filmreihe").resolve("poster.jpg")).exists();
        assertThat(flat.resolve("Matrix-poster.jpg")).doesNotExist();
        assertThat(kodi.resolve("Matrix")).doesNotExist();
    }

    @Test
    void keepsAPreviousSetFolderThatStillHoldsOtherFiles() throws IOException {
        files.save(both, "Matrix", ImageType.POSTER, new DownloadedImage(JPEG, "jpg"));
        Files.write(kodi.resolve("Matrix").resolve("Matrix.nfo"), JPEG);

        files.rename(both, "Matrix", "Matrix Filmreihe", List.of(ImageType.POSTER));

        assertThat(kodi.resolve("Matrix").resolve("Matrix.nfo")).exists();
        assertThat(kodi.resolve("Matrix Filmreihe").resolve("poster.jpg")).exists();
    }

    @Test
    void reportsAnUnwritableFolder() throws IOException {
        Files.writeString(directory.resolve("blocked"), "file, not folder");
        var blocked = new MovieSetSettings(true, directory.resolve("blocked").toString(), false, null, false);

        assertThatThrownBy(() -> files.save(blocked, TITLE, ImageType.POSTER, new DownloadedImage(JPEG, "jpg")))
                .isInstanceOf(ArtworkProblemException.class);
    }
}
