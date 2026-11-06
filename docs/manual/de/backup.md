# Sicherung

Die Seite **Sicherung** speichert alle KMM-Daten in einer Datei und spielt sie wieder ein. So kannst du dich vor Fehlern schützen oder KMM umziehen, auch auf eine andere Datenbank, zum Beispiel von Windows auf ein NAS mit MariaDB.

Zur Sicherung gehören Quellen, Filme, Serien, Episoden, Filmsets, Filter, Kodi-Geräte und Einstellungen. **Medien, NFOs und Bilder gehören nicht dazu.** Sie liegen in deinen Medienordnern und müssen separat gesichert werden. Das Einspielen ändert dort nichts und spricht Kodi nicht an.

Wenn ein Scan, Import oder anderer Job läuft, gehen Export und Import nicht. Warte dann, bis er fertig ist.

## Exportieren

**Sicherung herunterladen** speichert die Datei `kmm-sicherung-<Datum>.json.gz`.

**API-Keys und Passwörter einschließen** nimmt API-Keys, das Proxy-Passwort und die Passwörter der Kodi-Geräte mit. Standardmäßig bleiben sie draußen.

> **Achtung:** Mit Haken stehen diese Zugangsdaten im Klartext in der Datei. Bewahre sie sicher auf und gib sie nicht weiter.

## Importieren

1. Klicke auf **Sicherung wählen**. KMM zeigt, wann und womit die Datei erstellt wurde und was sie enthält. Noch wird nichts geändert.
2. Trage bei Bedarf die **Pfade** ein, siehe unten, und wähle die **Art des Imports**.
3. Klicke auf **Probelauf**. Er führt alles aus und macht es dann rückgängig, der Bericht zeigt also genau, was passieren würde.
4. Passt alles, klicke auf **Einspielen**.

### Ersetzen oder zusammenführen

**Ersetzen** stellt genau den Stand der Datei her und löscht alles andere. Zur Bestätigung gibst du das Wort `ERSETZEN` ein.

**Zusammenführen** behält, was nur in KMM steht. Stimmt ein Eintrag überein, **gewinnt die Datei**: Seine Angaben und Listen wie Darsteller und Genres werden durch die aus der Datei ersetzt. Auch die Einstellungen aus der Datei überschreiben die vorhandenen.

| Eintrag | gilt als gleich bei |
|---|---|
| Quelle | Pfad |
| Film | Dateipfad |
| Serie | Ordnerpfad |
| Episode | Dateipfad, Staffel und Episode |
| Filmset, Kodi-Gerät | Name |
| Filter | Art und Name |
| Einstellung | Schlüssel |

Namen gelten auch dann als gleich, wenn sie sich nur in Groß-/Kleinschreibung oder Akzenten unterscheiden. Überschneidet sich eine Quelle mit einer anderen oder ist ihr Name schon vergeben, überspringt KMM sie mitsamt ihrem Inhalt und nennt sie im Bericht.

### Pfade beim Umzug

Liegen deine Medien aus Sicht von KMM jetzt woanders, trage den neuen Pfad ein. Unter Docker ist das der Pfad **im Container**, zum Beispiel `/media/filme`, nicht der Pfad auf dem NAS.

Die Zuordnung gilt für Quellen, Filme, Serien, Episoden, die lokalen Pfade der Kodi-Geräte, ausgeschlossene Ordner und die Ordner für Set-Bilder. Pfade, auf die keine Regel passt, bleiben unverändert und werden im Bericht gezählt. Ist eine Quelle nicht erreichbar, steht das als Hinweis im Bericht.

### Zugangsdaten

Enthält die Datei keine Zugangsdaten, behält KMM die vorhandenen, auch beim Ersetzen. Kodi-Passwörter werden dabei über den Gerätenamen zugeordnet. Enthält sie welche, ersetzen sie die vorhandenen.

## Wenn etwas schiefgeht

Der Import läuft als ein Ganzes. Schlägt er fehl oder brichst du ihn ab, bleibt alles beim Alten.

Vor jedem echten Import speichert KMM den bisherigen Stand unter `backup/vor-import-<Zeit>.json.gz` im KMM-Datenordner und behält die letzten drei. Unter Docker liegt der Ordner in `/config`. Der Bericht nennt den genauen Pfad. **Diese Dateien enthalten die Zugangsdaten.** Um einen Import rückgängig zu machen, lädst du die Datei herunter und spielst sie mit **Ersetzen** ein.

Eine Sicherung aus einer neueren KMM-Version lässt sich erst einspielen, wenn du KMM aktualisiert hast. Ältere Sicherungen funktionieren.
