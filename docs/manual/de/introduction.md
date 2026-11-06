# Einführung

Der Kodi Media Manager (KMM) pflegt deine Film- und Serienbibliothek für [Kodi](https://kodi.tv): Er findet deine Dateien, holt Angaben und Bilder aus dem Internet, schreibt sie so ab, wie Kodi sie erwartet, und sagt Kodi Bescheid, wenn sich etwas geändert hat. KMM ist eine Weiterentwicklung von Ember Media Manager und kann dessen Daten übernehmen.

Du bedienst KMM im Browser. Es läuft auf deinem Rechner oder als Container auf einem Server.

## Was KMM kann

- **Bibliothek:** Filme, Serien, Staffeln und Episoden aus deinen Ordnern einlesen.
- **Online-Daten:** Metadaten, Bilder, Trailer-Links, Titelmusik und Bewertungen von mehreren Anbietern holen, einzeln oder für eine ganze Quelle.
- **Technische Angaben:** Auflösung, Codecs, Tonspuren und Untertitel mit ffprobe aus den Dateien auslesen.
- **Ordnung:** Dateien und Ordner nach deinen Mustern umbenennen, Filmsets und Tags verwalten.
- **Auswahl:** Listen filtern, Filter speichern und viele Einträge auf einmal bearbeiten.
- **Kodi:** mehrere Kodi-Geräte automatisch aktualisieren und den Gesehen-Status aus Kodi holen.
- **Export:** gefilterte Listen als HTML, CSV oder JSON herunterladen.

## Wie KMM mit deinen Daten umgeht

Das solltest du wissen, bevor du loslegst:

- **NFO-Dateien:** KMM speichert Angaben in einer Datenbank und zusätzlich in NFO-Dateien neben deinen Videos. Kodi liest diese Dateien, und sie bleiben auch ohne KMM nutzbar.
- **Speichern schreibt in deine Medienordner:** Wenn du einen Film speicherst, einen Trailer einträgst oder ein Bild übernimmst, ändert KMM Dateien neben dem Video. Auch der Scan kann schreiben: Bei neuen Dateien trägt er die technischen Angaben in die NFO ein, das ist voreingestellt und [abschaltbar](settings.md#technische-angaben).
- **Vorhandenes bleibt:** Das automatische Scrapen füllt nur, was fehlt. Es überschreibt keine Angaben und keine Bilder, die schon da sind.
- **Umbenennen ist abschaltbar:** Automatisches Umbenennen ist anfangs aus. Vor dem Umbenennen einer ganzen Quelle siehst du immer eine Vorschau.
- **Ember bleibt unberührt:** Beim Ember-Import wird dein Ember-Profil nur gelesen.

Mache vor größeren Aktionen trotzdem eine Sicherung deiner NFO-Dateien, vor allem beim ersten Umbenennen einer ganzen Bibliothek.

## Wo es weitergeht

Beginne mit [Installation und Start](installation.md) und danach mit den [ersten Schritten](getting-started.md). Wenn etwas nicht klappt, hilft die [Fehlerbehebung](troubleshooting.md).
