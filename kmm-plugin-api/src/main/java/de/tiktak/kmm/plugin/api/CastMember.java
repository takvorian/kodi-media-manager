package de.tiktak.kmm.plugin.api;

import java.net.URI;
import java.util.Objects;

/**
 * Eine Person der Besetzung.
 *
 * @param role  Rolle oder {@code null}
 * @param thumb Porträt oder {@code null}
 */
public record CastMember(String name, String role, URI thumb) {

    public CastMember {
        Objects.requireNonNull(name, "name must not be null");
    }
}
