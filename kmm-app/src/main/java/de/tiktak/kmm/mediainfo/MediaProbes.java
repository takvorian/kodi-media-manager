package de.tiktak.kmm.mediainfo;

import java.time.Duration;

import org.springframework.stereotype.Component;

import de.tiktak.kmm.media.FfprobeRunner;
import de.tiktak.kmm.media.MediaProbe;
import de.tiktak.kmm.settings.SettingsService;
import lombok.RequiredArgsConstructor;

/**
 * Erzeugt für jede Untersuchung einen {@link MediaProbe} mit dem eingestellten ffprobe-Pfad.
 */
@Component
@RequiredArgsConstructor
class MediaProbes {

    /** Auf Netzlaufwerken braucht ffprobe Sekunden, bei großen DVD-Titeln auch länger. */
    static final Duration TIMEOUT = Duration.ofMinutes(2);

    private final SettingsService settingsService;

    MediaProbe current() {
        return new MediaProbe(new FfprobeRunner(settingsService.current().mediaInfo().ffprobePath(), TIMEOUT));
    }

    boolean probeOnScrape() {
        return settingsService.current().mediaInfo().probeOnScrape();
    }

    boolean probeNewFiles() {
        return settingsService.current().mediaInfo().probeNewFiles();
    }
}
