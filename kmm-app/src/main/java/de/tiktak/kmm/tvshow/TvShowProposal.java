package de.tiktak.kmm.tvshow;

/**
 * Vorschlag aus der Online-Suche für das Formular der Serie.
 *
 * @param metadata         die vorhandenen Metadaten, überlagert mit den Daten des Treffers
 * @param matchedEpisodes  Episoden der Bibliothek, die der Anbieter kennt und beim Übernehmen ergänzt
 * @param providerEpisodes Episoden beim Anbieter insgesamt
 */
public record TvShowProposal(TvShowMetadata metadata, int matchedEpisodes, int providerEpisodes) {
}
