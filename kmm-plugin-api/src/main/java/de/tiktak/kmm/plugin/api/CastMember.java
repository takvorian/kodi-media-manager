package de.tiktak.kmm.plugin.api;

import java.net.URI;
import java.util.Objects;

/**
 * A member of the cast.
 *
 * @param role  role or {@code null}
 * @param thumb portrait or {@code null}
 */
public record CastMember(String name, String role, URI thumb) {

    public CastMember {
        Objects.requireNonNull(name, "name must not be null");
    }
}
