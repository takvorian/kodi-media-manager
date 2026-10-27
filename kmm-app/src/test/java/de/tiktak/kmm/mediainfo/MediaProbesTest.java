package de.tiktak.kmm.mediainfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tiktak.kmm.media.MediaProbeException;
import de.tiktak.kmm.scanner.DiscFormat;
import de.tiktak.kmm.settings.KmmSettings;
import de.tiktak.kmm.settings.MediaInfoSettings;
import de.tiktak.kmm.settings.SettingsService;

class MediaProbesTest {

    private final SettingsService settingsService = mock(SettingsService.class);
    private final MediaProbes probes = new MediaProbes(settingsService);

    @Test
    void usesTheConfiguredFfprobeAndSwitches() {
        givenMediaInfo(new MediaInfoSettings("/fehlt/ffprobe", false, true));

        assertThat(probes.probeOnScrape()).isFalse();
        assertThat(probes.probeNewFiles()).isTrue();
        assertThatThrownBy(() -> probes.current().probe(Path.of("film.mkv"), DiscFormat.FILE))
                .isInstanceOf(MediaProbeException.class).hasMessageContaining("/fehlt/ffprobe");
    }

    private void givenMediaInfo(MediaInfoSettings mediaInfo) {
        KmmSettings settings = new KmmSettings("de-DE", "de", List.of("mkv"), List.of(), null, null, mediaInfo);
        when(settingsService.current()).thenReturn(settings);
    }
}
