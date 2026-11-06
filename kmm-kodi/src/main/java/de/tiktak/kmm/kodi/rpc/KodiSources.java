package de.tiktak.kmm.kodi.rpc;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * The video sources of a Kodi device. A scan outside these sources ends in Kodi without result and without error.
 * Credentials in the addresses of the sources are dropped because the paths appear in messages and in the log.
 */
public final class KodiSources {

    private static final String MULTIPATH_PREFIX = "multipath://";
    private static final String ADDONS_PREFIX = "addons://";
    private static final Pattern CREDENTIALS = Pattern.compile("^([a-zA-Z]+://)[^/@]+@");

    private final List<String> paths;

    private KodiSources(List<String> paths) {
        this.paths = paths;
    }

    /** @param files sources as Kodi returns them, including combined ones ({@code multipath://}) and add-ons */
    static KodiSources of(Collection<String> files) {
        return new KodiSources(files.stream()
                .flatMap(KodiSources::singlePaths)
                .filter(file -> !file.startsWith(ADDONS_PREFIX))
                .map(file -> CREDENTIALS.matcher(file).replaceFirst("$1"))
                .toList());
    }

    /** @return whether the directory lies in or below a source */
    public boolean contain(String directory) {
        String wanted = comparable(directory);
        return paths.stream().map(KodiSources::comparable).anyMatch(wanted::startsWith);
    }

    /** @return whether the path lies in a source or contains one; a path mapping may be wider */
    public boolean overlap(String path) {
        String wanted = comparable(path);
        return contain(path) || paths.stream().map(KodiSources::comparable).anyMatch(source -> source.startsWith(wanted));
    }

    /** @return the sources for a message, a translatable "none" if there are none */
    public LocalizedText describe() {
        return paths.isEmpty() ? LocalizedText.of("kodiRpc.noSources") : LocalizedText.literal(toString());
    }

    /** @return the sources separated by commas, empty if there are none */
    @Override
    public String toString() {
        return String.join(", ", paths);
    }

    private static Stream<String> singlePaths(String file) {
        if (!file.startsWith(MULTIPATH_PREFIX)) {
            return Stream.of(file);
        }
        return Arrays.stream(file.substring(MULTIPATH_PREFIX.length()).split("/"))
                .filter(part -> !part.isEmpty())
                .map(part -> URLDecoder.decode(part, StandardCharsets.UTF_8));
    }

    /** Kodi does not compare paths consistently; the check is only meant to report clearly wrong paths. */
    private static String comparable(String path) {
        String normalized = CREDENTIALS.matcher(path).replaceFirst("$1").replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }
}
