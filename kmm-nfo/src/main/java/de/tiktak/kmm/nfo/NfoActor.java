package de.tiktak.kmm.nfo;

import java.util.Objects;

/**
 * Ein Darsteller ({@code <actor>}).
 *
 * @param name  Name, Pflicht
 * @param role  Rolle oder {@code null}
 * @param order Reihenfolge laut NFO oder {@code null}
 * @param thumb URL oder Pfad des Bildes oder {@code null}
 */
public record NfoActor(String name, String role, Integer order, String thumb) {

    public NfoActor {
        Objects.requireNonNull(name, "name must not be null");
    }
}
