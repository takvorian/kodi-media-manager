package de.tiktak.kmm.nfo;

import java.util.Objects;

/**
 * A rating ({@code <ratings><rating>}).
 *
 * @param name         source, e.g. {@code imdb} or {@code themoviedb}
 * @param value        value
 * @param max          maximum of the scale or {@code null}
 * @param votes        number of votes or {@code null}
 * @param defaultValue whether Kodi shows this rating
 */
public record NfoRating(String name, double value, Integer max, Integer votes, boolean defaultValue) {

    public NfoRating {
        Objects.requireNonNull(name, "name must not be null");
    }
}
