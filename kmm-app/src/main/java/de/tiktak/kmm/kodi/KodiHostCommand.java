package de.tiktak.kmm.kodi;

import java.util.List;

import de.tiktak.kmm.kodi.path.KodiPathMapping;
import de.tiktak.kmm.kodi.rpc.KodiEndpoint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Creating or changing a Kodi device via the REST API.
 *
 * @param address  host name or IP address without {@code http://}
 * @param password new password; {@code null} keeps the stored one
 */
public record KodiHostCommand(
        @NotBlank @Size(max = KodiHostSettings.MAX_NAME_LENGTH) String name,
        @NotBlank @Size(max = KodiHostSettings.MAX_ADDRESS_LENGTH) @Pattern(regexp = ADDRESS_PATTERN) String address,
        @Min(KodiEndpoint.MIN_PORT) @Max(KodiEndpoint.MAX_PORT) int port,
        @Size(max = KodiHostSettings.MAX_USERNAME_LENGTH) String username,
        @Size(max = KodiHostSettings.MAX_PASSWORD_LENGTH) String password,
        boolean realtimeSync,
        @NotNull @Size(max = MAX_MAPPINGS) List<@Valid @NotNull PathMapping> pathMappings) {

    static final int MAX_MAPPINGS = 20;
    /** Without scheme, path or spaces. */
    private static final String ADDRESS_PATTERN = "\\s*[^\\s/]+\\s*";

    KodiHostSettings toSettings() {
        return new KodiHostSettings(name, address, port, username, password, realtimeSync,
                pathMappings.stream().map(PathMapping::toMapping).toList());
    }

    /**
     * @param localPath directory as KMM sees it, e.g. {@code /media/filme}
     * @param kodiPath  the same directory as Kodi sees it, e.g. {@code smb://nas/filme/}
     */
    public record PathMapping(
            @NotBlank @Size(max = KodiHostSettings.MAX_PATH_LENGTH) String localPath,
            @NotBlank @Size(max = KodiHostSettings.MAX_PATH_LENGTH) String kodiPath) {

        static PathMapping of(KodiPathMapping mapping) {
            return new PathMapping(mapping.localPath(), mapping.kodiPath());
        }

        KodiPathMapping toMapping() {
            return new KodiPathMapping(localPath, kodiPath);
        }
    }
}
