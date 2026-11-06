package de.tiktak.kmm.nfo;

import java.util.Objects;

/**
 * An actor ({@code <actor>}).
 *
 * @param name  name, required
 * @param role  role or {@code null}
 * @param order order according to the NFO or {@code null}
 * @param thumb URL or path of the image or {@code null}
 */
public record NfoActor(String name, String role, Integer order, String thumb) {

    public NfoActor {
        Objects.requireNonNull(name, "name must not be null");
    }
}
