package de.tiktak.kmm.kodi.rpc;

/**
 * Name und Version der Kodi-Installation, z. B. „Kodi 21.2“.
 */
public record KodiVersion(String name, int major, int minor) {

    @Override
    public String toString() {
        return "%s %d.%d".formatted(name, major, minor);
    }
}
