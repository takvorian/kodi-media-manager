# Einstellungen

Änderungen werden erst mit **Speichern** am Ende der Seite wirksam. **Verwerfen** stellt den gespeicherten Stand wieder her. Die Scraper haben einen eigenen Reiter, siehe [Scraper](settings.md#scrapers).

Steht bei einem Feld „Durch die Konfiguration festgelegt“, hat jemand den Wert über eine Umgebungsvariable gesetzt, er ist dann hier gesperrt.

## Sprache der Oberfläche

Die Oberfläche gibt es auf Deutsch und Englisch. Umschalten kannst du jederzeit oben rechts neben dem **?** (**DE**/**EN**); die Seite wird dabei nicht neu geladen, und auch das Handbuch wechselt die Sprache. KMM merkt sich die Wahl im Browser.

Beim Öffnen gilt in dieser Reihenfolge:

1. der Parameter `lang` in der Adresse, zum Beispiel `http://nas:8080/?lang=de`; erlaubt sind auch `de_DE`, `de-DE`, `en` oder `en_GB`,
2. die zuletzt gewählte Sprache in diesem Browser,
3. sonst Englisch.

Datum und Zahlen richten sich nach der Sprache der Oberfläche: deutsch wie `25.10.2026`, englisch britisch wie `25/10/2026`. Die Sprache der Metadaten, die KMM bei den Anbietern abfragt, ist davon unabhängig, siehe den nächsten Abschnitt.

## Sprache

Gilt für Filme, wenn weder der Film noch seine Quelle eine eigene Sprache haben, siehe [Sprache je Eintrag](scraping.md#sprache-je-quelle-und-eintrag).

- **Metadaten:** zum Beispiel `de-DE` oder `en-US`.
- **Bevorzugte Bildsprache:** zweistellig, zum Beispiel `de`. Solche Bilder erscheinen in der Auswahl zuerst.

## Serien

Dieselben beiden Felder für Serien, dazu die **Episodenreihenfolge**: Ausstrahlung, DVD/Blu-ray oder Absolut (ohne Staffeln). Das gilt für das Scrapen und wirkt nur bei TVDB. Für einzelne Serien wählst du eine andere Zählung in der Serienansicht, siehe [Episoden-Reihenfolge](tv-shows.md#episode-order).

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

<a id="scrapers"></a>
## Scraper

Die Einstellungen haben oben zwei Reiter: **Allgemein** mit allem bisher Beschriebenen und **Scraper**. Unter **Scraper** steht jeder Anbieter mit dem, was er liefert (Metadaten, Bilder, Bewertungen und Details, Titelmusik, Trailer, Filmsets) und für welche Medien. Dazu sein Zustand:

- **bereit**: KMM nutzt ihn.
- **Einstellung fehlt**: Meist fehlt der Key, darunter steht, was genau. KMM überspringt den Anbieter, bis du es nachträgst.
- **ausgeschaltet**: KMM fragt ihn nicht.

Ein Klick auf einen Anbieter öffnet seine Seite. Dort schaltest du ihn mit **Eingeschaltet** an oder aus und änderst unter **Keys und Optionen** seine Einstellungen. Klicke danach auf **Speichern**.

<a id="scraper-order"></a>
### Reihenfolge

Unter der Übersicht legst du für **Filme** und **Serien** fest, in welcher Reihenfolge KMM die Anbieter je Art fragt. Mit den Pfeilen verschiebst du einen Anbieter, mit dem Haken schaltest du ihn für diese Art ab. Klicke danach auf **Speichern**.

- **Metadaten:** Der erste Anbieter ordnet neue Filme und Serien zu. Findet er nichts, versucht es der nächste. Beim Neu-Holen gilt der erste, der eingerichtet ist.
- **Bilder:** KMM fragt alle angehakten Anbieter. Innerhalb einer Sprache stehen die Bilder des vorderen Anbieters zuerst, und die Automatik nimmt das erste Bild.
- **Bewertungen und Details:** Füllt ein leeres Feld, gewinnt der vordere Anbieter.
- **Titelmusik, Trailer, Filmsets:** in dieser Reihenfolge.

Ohne eigene Wahl steht bei Filmen TMDB und bei Serien TVDB vorn, die übrigen folgen nach Name. Bei Bewertungen gilt eine feste Reihenfolge. Für einzelne Quellen kannst du abweichen, siehe [Scraper einer Quelle](sources.md#source-scrapers). Eine eigene Episoden-Reihenfolge einer Serie legt ihren Anbieter fest, siehe [Episoden-Reihenfolge](tv-shows.md#episode-order).

<a id="api-keys"></a>
### Keys

| Anbieter | Key | Wofür | Woher |
|---|---|---|---|
| TMDB | API-Key | Filme, Serien, Bilder, Trailer | <https://www.themoviedb.org/settings/api> |
| TVDB | Projekt-Key, bei einem Abonnement auch die PIN | Serien, Bilder | <https://thetvdb.com/api-information> |
| fanart.tv | Projekt-Key, optional ein persönlicher Key | Bilder | <https://fanart.tv/get-an-api-key/> |
| OMDb | API-Key | Bewertungen, englische Angaben | <https://www.omdbapi.com/apikey.aspx> |
| Trakt | Client-ID | Community-Bewertung | <https://trakt.tv/oauth/applications> |

KMM speichert die Keys in seiner Datenbank und zeigt nur ihre letzten Zeichen an. Ein leeres Feld lässt den gespeicherten Key unverändert, **Entfernen** löscht ihn.

Umgebungsvariablen wie `KMM_TMDB_API_KEY` füllen nur einen noch nicht gespeicherten Key beim Start. Danach gilt der gespeicherte, und ein gelöschter Key bleibt gelöscht.

### Weitere Optionen

- **TMDB, TVDB:** das Land der Altersfreigabe, zum Beispiel `DE` oder `US`.
- **Moviepilot, OFDb:** die Pause zwischen zwei Anfragen in Millisekunden. So belastet KMM die Seiten nicht.
- **YouTube:** das Programm yt-dlp, der Ordner von ffmpeg und die Anzahl der Suchtreffer für Titelmusik.

Unter jedem Feld steht der Standard. Leerst du ein Feld, gilt wieder der Standard. Werte aus Umgebungsvariablen wie `KMM_YTDLP` sind dieser Standard.

## Umgebungsvariablen

Für Filme haben diese Variablen Vorrang vor den Feldern oben: `KMM_TMDB_LANGUAGE`, `KMM_ARTWORK_LANGUAGE`, `KMM_PROXY_HOST`, `KMM_PROXY_PORT`, `KMM_PROXY_USERNAME`, `KMM_PROXY_PASSWORD`. Weitere stehen unter [Installation](installation.md#zugriff-aus-dem-heimnetz).
