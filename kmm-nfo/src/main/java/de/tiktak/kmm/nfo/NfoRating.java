package de.tiktak.kmm.nfo;

import java.util.Objects;

/**
 * Eine Bewertung ({@code <ratings><rating>}).
 *
 * @param name         Quelle, z. B. {@code imdb} oder {@code themoviedb}
 * @param value        Wert
 * @param max          Höchstwert der Skala oder {@code null}
 * @param votes        Anzahl der Stimmen oder {@code null}
 * @param defaultValue ob Kodi diese Bewertung anzeigt
 */
public record NfoRating(String name, double value, Integer max, Integer votes, boolean defaultValue) {

    public NfoRating {
        Objects.requireNonNull(name, "name must not be null");
    }
}
