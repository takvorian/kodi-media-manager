# Export

Dieser Export liefert Listen zum Ansehen und Weiterverarbeiten, er lässt sich nicht wieder einlesen. Um KMM zu sichern oder umzuziehen, nutze die [Sicherung](backup.md).

In der Film- und der Serienliste lädt **Exportieren** die aktuelle Liste herunter. Exportiert wird genau, was der gewählte [Filter](filters.md) samt Suche zeigt, also auch die Einträge auf den weiteren Seiten. Spaltenköpfe, „ja“/„nein“, Zahlen und Datum folgen der Sprache der Oberfläche.

## Formate

| Format | Inhalt |
|---|---|
| **HTML-Liste mit Postern (ZIP)** | Eine eigenständige Liste mit Suchfeld und verkleinerten Postern. Entpacke das ZIP und öffne die HTML-Datei im Browser. Das Ergebnis eignet sich zum Weitergeben. |
| **CSV für Excel** | Bei deutscher Oberfläche mit Semikolon und Dezimalkomma, bei englischer mit Komma und Dezimalpunkt, so wie Excel es in der jeweiligen Sprache erwartet. Umlaute werden richtig angezeigt. „ja“ und „nein“ stehen als Text da. |
| **JSON** | Für die Weiterverarbeitung in anderen Programmen. Listen sind echte Arrays. |

## Spalten

**Filme:** Titel, Originaltitel, Jahr, Genres, Laufzeit, Freigabe, Bewertung, eigene Bewertung, Gesehen, Auflösung, Videocodec, Tonspuren, Untertitel, Filmset, Tags, IMDb, TMDB, Datei, Hinzugefügt.

**Serien:** Titel, Originaltitel, Jahr, Status, Genres, Sender, Staffeln, Episoden, ungesehene Episoden, Bewertung, eigene Bewertung, Tags, TVDB, TMDB, IMDb, Ordner, Hinzugefügt.

Die Spalten **Datei** und **Ordner** enthalten deine Pfade. Das solltest du beachten, wenn du einen Export weitergibst.
