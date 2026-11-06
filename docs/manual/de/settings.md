# Einstellungen

Änderungen werden erst mit **Speichern** am Ende der Seite wirksam. **Verwerfen** stellt den gespeicherten Stand wieder her. Die API-Keys haben einen eigenen Knopf.

Steht bei einem Feld „Durch die Konfiguration festgelegt“, hat jemand den Wert über eine Umgebungsvariable gesetzt, er ist dann hier gesperrt.

## Sprache

Gilt für Filme, wenn weder der Film noch seine Quelle eine eigene Sprache haben, siehe [Sprache je Eintrag](scraping.md#sprache-je-quelle-und-eintrag).

- **Metadaten:** zum Beispiel `de-DE` oder `en-US`.
- **Bevorzugte Bildsprache:** zweistellig, zum Beispiel `de`. Solche Bilder erscheinen in der Auswahl zuerst.

## Serien

Dieselben beiden Felder für Serien, dazu die **Episodenreihenfolge**: Ausstrahlung, DVD/Blu-ray oder Absolut (ohne Staffeln). Das gilt für das Scrapen und wirkt nur bei TVDB.

## Technische Angaben

- **ffprobe:** der Pfad zu ffprobe aus FFmpeg, oder nur `ffprobe`, wenn es im Suchpfad liegt.
- **Nach dem automatischen Scrapen auslesen**.
- **Neue Dateien ohne Angaben in der NFO nach dem Scan auslesen**.

## Umbenennen

Die Muster und die automatischen Auslöser, ausführlich erklärt unter [Umbenennen](renaming.md).

## Filmsets

- **Filme beim Scrapen ihrem Filmset zuordnen:** Ersetzt ein anderes Set des Films.
- **Set-Bilder im flachen Schema ablegen:** wie Ember, als `<Ordner>/<Settitel>-poster.jpg`.
- **Set-Bilder in Kodis Set-Ordnern ablegen:** als `<Ordner>/<Settitel>/poster.jpg`. Der Ordner muss der in Kodi eingestellte „Ordner für Filmset-Informationen“ sein.

Mehr dazu unter [Filmsets](movie-sets.md).

## Scan

- **Video-Dateiendungen:** durch Komma getrennt, zum Beispiel `mkv, mp4, avi`.
- **Ausgeschlossene Ordner:** ein vollständiger Pfad pro Zeile. Der Scan überspringt sie samt Inhalt.

## Proxy

Für den Fall, dass KMM nur über einen Proxy ins Internet kommt: Host, Port und bei Bedarf Benutzer und Passwort. Kodi wird nie über den Proxy angesprochen.

## API-Keys

| Key | Wofür | Woher |
|---|---|---|
| TMDB-API-Key | Filme, Serien, Bilder, Trailer | <https://www.themoviedb.org/settings/api> |
| TVDB-API-Key | Serien, Bilder | <https://thetvdb.com/api-information> |
| fanart.tv-API-Key | Bilder | <https://fanart.tv/get-an-api-key/> |
| OMDb-API-Key | Bewertungen, englische Angaben | <https://www.omdbapi.com/apikey.aspx> |
| Trakt-Client-ID | Community-Bewertung | <https://trakt.tv/oauth/applications> |

KMM speichert die Keys in seiner Datenbank und zeigt nur ihre letzten Zeichen an. Ein leeres Feld lässt den gespeicherten Key unverändert, **Entfernen** löscht ihn. Klicke am Ende auf **Keys speichern**.

Umgebungsvariablen wie `KMM_TMDB_API_KEY` füllen nur einen noch nicht gespeicherten Key beim Start. Danach gilt der gespeicherte, und ein gelöschter Key bleibt gelöscht.

## Umgebungsvariablen

Für Filme haben diese Variablen Vorrang vor den Feldern oben: `KMM_TMDB_LANGUAGE`, `KMM_ARTWORK_LANGUAGE`, `KMM_PROXY_HOST`, `KMM_PROXY_PORT`, `KMM_PROXY_USERNAME`, `KMM_PROXY_PASSWORD`. Weitere stehen unter [Installation](installation.md#zugriff-aus-dem-heimnetz).
