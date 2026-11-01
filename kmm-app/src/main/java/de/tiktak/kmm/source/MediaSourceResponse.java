package de.tiktak.kmm.source;

import java.time.Instant;

import de.tiktak.kmm.plugin.api.MediaType;

/**
 * Darstellung einer Quelle für die REST-API.
 */
public record MediaSourceResponse(
        long id,
        String name,
        String path,
        MediaType mediaType,
        Instant createdAt,
        Instant lastScannedAt,
        String metadataLanguage) {

    static MediaSourceResponse from(MediaSource source) {
        return new MediaSourceResponse(source.getId(), source.getName(), source.getPath(), source.getMediaType(),
                source.getCreatedAt(), source.getLastScannedAt(), source.getMetadataLanguage());
    }
}
