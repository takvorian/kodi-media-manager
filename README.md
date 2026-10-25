# Kodi Media Manager (KMM)

Medienverwaltung für [Kodi](https://kodi.tv) als Java-Nachfolger von [Ember Media Manager](https://github.com/DanCooper/Ember-MM-Newscraper).
KMM läuft als Docker-Container (mit MariaDB) oder standalone (mit eingebetteter H2-Datenbank) und bringt eine Weboberfläche mit.

> **Status:** Filme-MVP in Arbeit – Quellen anlegen, scannen, Filmliste sowie Kodi-NFOs lesen, bearbeiten und schreiben funktionieren. Konzept und Roadmap: [docs/KONZEPT.md](docs/KONZEPT.md).

## Technik

- Java 25, Spring Boot 4.1, Spring Modulith, Hibernate 7, Flyway
- Scraper als Plugins ([PF4J](https://pf4j.org))
- Angular 22 + Angular Material
- MariaDB (Docker), H2 (standalone), PostgreSQL optional – alle drei per Testcontainers bzw. H2 getestet

## Module

| Modul | Inhalt |
|---|---|
| `kmm-plugin-api` | Schnittstelle für Scraper-Plugins |
| `kmm-scanner` | Filmsuche im Dateisystem, Titel/Jahr aus Datei- und Ordnernamen |
| `kmm-nfo` | Kodi-NFOs lesen und schreiben (unbekannte Elemente bleiben erhalten) |
| `kmm-migration-ember` | Lesen von Ember-Profilen (Datenbank + `Settings.xml`), Pfad-Mapping |
| `kmm-plugins` | mitgelieferte Plugins |
| `kmm-ui` | Angular-Oberfläche |
| `kmm-app` | Spring-Boot-Anwendung |

## Bauen

Voraussetzung: JDK 25. Maven und Node.js lädt der Build selbst herunter.

```bash
./mvnw verify          # Build inkl. Java- und Angular-Tests, Coverage-Prüfung (≥ 80 %)
./mvnw -DskipTests package
```

Der Test gegen MariaDB (Testcontainers) läuft nur mit Docker und wird sonst übersprungen.

## Starten

### Standalone

```bash
java -jar kmm-app/target/kmm-app.jar
```

Die Oberfläche ist dann unter <http://localhost:8080> erreichbar. Daten liegen unter `~/.kodi-media-manager` (änderbar über `KMM_HOME`).
Eine andere Datenbank lässt sich z. B. so verwenden:

```bash
java -jar kmm-app/target/kmm-app.jar \
  --spring.datasource.url=jdbc:postgresql://localhost/kmm \
  --spring.datasource.username=kmm --spring.datasource.password=…
```

### Docker

```bash
cd docker
cp .env.example .env     # Passwörter setzen
docker compose up -d --build
```

| Volume | Zweck |
|---|---|
| `/config` | Plugins, Cache, Logs |
| `/media` | Medienbibliothek |
| `/import/ember` | Ember-Profil für die Migration (read-only) |

## Entwicklung

```bash
./mvnw -pl kmm-app -am spring-boot:run   # Backend auf :8080
cd kmm-ui && npm start                   # Angular-Dev-Server auf :4200, Proxy auf das Backend
```

## API

- OpenAPI/Swagger UI: `/swagger-ui.html` (im Docker-Profil mit Anmeldung)
- `GET /api/v1/system/info`
- `GET /api/v1/sources`, `POST /api/v1/sources`, `DELETE /api/v1/sources/{id}`
- `POST /api/v1/sources/{id}/scan`
- `GET /api/v1/movies?search=…&page=…&size=…`
- `GET /api/v1/movies/{id}`, `PUT /api/v1/movies/{id}/metadata` (schreibt die NFO)
- `GET /api/v1/plugins`, `POST /api/v1/plugins/{id}/enable|disable`
- `GET /api/v1/migrations/ember/inspect?path=…`
