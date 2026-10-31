package de.tiktak.kmm.movie;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.tiktak.kmm.source.ScanDetails;

/**
 * Ergebnis eines Quellen-Scans.
 *
 * @param added                neu gefundene Filme
 * @param updated              bekannte Filme, deren NFO sich geändert hat und neu eingelesen wurde
 * @param removed              Filme, deren Datei nicht mehr existiert
 * @param unchanged            bekannte Filme ohne Änderung
 * @param nfoErrors            NFOs, die nicht gelesen werden konnten (die Filme bleiben trotzdem erhalten)
 * @param details              die betroffenen Filme, gekürzt
 * @param newWithoutStreams    IDs der neu gefundenen Filme ohne technische Angaben in der NFO
 */
public record ScanResult(long sourceId, int added, int updated, int removed, int unchanged, int nfoErrors,
        ScanDetails details, @JsonIgnore List<Long> newWithoutStreams) {

    public ScanResult {
        details = details == null ? ScanDetails.EMPTY : details;
        newWithoutStreams = newWithoutStreams == null ? List.of()
                : newWithoutStreams.stream().filter(Objects::nonNull).toList();
    }

    public ScanResult(long sourceId, int added, int updated, int removed, int unchanged, int nfoErrors) {
        this(sourceId, added, updated, removed, unchanged, nfoErrors, ScanDetails.EMPTY, List.of());
    }
}
