package de.tiktak.kmm.tvshow;

/**
 * Proposal from the online search for the TV show form.
 *
 * @param metadata         the existing metadata, overlaid with the data of the hit
 * @param matchedEpisodes  episodes of the library that the provider knows and enriches when applied
 * @param providerEpisodes total episodes at the provider
 */
public record TvShowProposal(TvShowMetadata metadata, int matchedEpisodes, int providerEpisodes) {
}
