-- Eigene, minimale Nachbildung des Ember-Schemas v48 (nur die von KMM gelesenen Tabellen und Spalten).
CREATE TABLE moviesource (idSource INTEGER PRIMARY KEY, strName TEXT, strPath TEXT, strLanguage TEXT);
CREATE TABLE tvshowsource (idSource INTEGER PRIMARY KEY, strName TEXT, strPath TEXT);
CREATE TABLE movie (
    idMovie INTEGER PRIMARY KEY, idSource INTEGER, MoviePath TEXT, Title TEXT, OriginalTitle TEXT, SortTitle TEXT,
    Year TEXT, premiered TEXT, Plot TEXT, Outline TEXT, Tagline TEXT, Runtime TEXT, MPAA TEXT, Certification TEXT,
    Imdb TEXT, TMDB TEXT, TMDBColID TEXT, DateAdded INTEGER, Playcount INTEGER, iLastPlayed INTEGER,
    iUserRating INTEGER);
CREATE TABLE uniqueid (idUniqueID INTEGER PRIMARY KEY, media_id INTEGER, media_type TEXT, value TEXT, type TEXT,
    isDefault BOOLEAN);
CREATE TABLE genre (idGenre INTEGER PRIMARY KEY, strGenre TEXT);
CREATE TABLE genrelinkmovie (idGenre INTEGER, idMovie INTEGER, sorting INTEGER);
CREATE TABLE actors (idActor INTEGER PRIMARY KEY, strActor TEXT, strThumb TEXT);
CREATE TABLE actorlinkmovie (idActor INTEGER, idMovie INTEGER, strRole TEXT, iOrder INTEGER);
CREATE TABLE directorlinkmovie (idDirector INTEGER, idMovie INTEGER);
CREATE TABLE writerlinkmovie (idWriter INTEGER, idMovie INTEGER);
CREATE TABLE studio (idStudio INTEGER PRIMARY KEY, strStudio TEXT);
CREATE TABLE studiolinkmovie (idStudio INTEGER, idMovie INTEGER);
CREATE TABLE country (idCountry INTEGER PRIMARY KEY, strCountry TEXT);
CREATE TABLE countrylinkmovie (idCountry INTEGER, idMovie INTEGER);
CREATE TABLE tag (idTag INTEGER PRIMARY KEY, strTag TEXT);
CREATE TABLE taglinks (idTag INTEGER, idMedia INTEGER, media_type TEXT, sorting INTEGER);
CREATE TABLE rating (idRating INTEGER PRIMARY KEY, media_id INTEGER, media_type TEXT, rating_type TEXT,
    rating_max INTEGER, rating FLOAT, votes INTEGER, isDefault BOOLEAN);
CREATE TABLE sets (idSet INTEGER PRIMARY KEY, Title TEXT, Plot TEXT, TMDBColID TEXT);
CREATE TABLE setlinkmovie (idSet INTEGER, idMovie INTEGER, iOrder INTEGER);
CREATE TABLE ExcludeDir (Dirname TEXT PRIMARY KEY);
CREATE TABLE tvshow (idShow INTEGER PRIMARY KEY, idSource INTEGER, TVShowPath TEXT);
CREATE TABLE episode (idEpisode INTEGER PRIMARY KEY, idShow INTEGER, Title TEXT);

INSERT INTO moviesource VALUES (1, 'Filme', 'D:\Filme', 'de-DE');
INSERT INTO tvshowsource VALUES (1, 'Serien', '\\nas\serien');

INSERT INTO movie VALUES (1, 1, 'D:\Filme\Heat (1995)\Heat.mkv', 'Heat', 'Heat', '', '1995', '1995-12-15',
    'Neil McCauley plant einen letzten Coup.', '', 'A Los Angeles Crime Saga', '170', 'Germany:16', 'Germany:16',
    'tt0113277', '949', '', 1480465215, 2, 1471131295, 9);
INSERT INTO movie VALUES (2, 1, 'D:\Filme\The Matrix (1999)\The Matrix.mkv', 'Matrix', 'The Matrix', 'Matrix 1',
    '1999', '1999-06-17', 'Neo erfährt die Wahrheit.', 'Kurz', '', '136 min', 'Germany:16', 'Germany:16', 'tt0133093',
    '603', '2344', 1480465216, NULL, NULL, 0);
INSERT INTO movie VALUES (3, 1, 'D:\Filme\The Matrix Reloaded (2003)\The Matrix Reloaded CD1.avi',
    'Matrix Reloaded', '', '', '', 'unbekannt', '', '', '', '', '', 'Germany:12', 'tt0234215', '', '', 0, 0, 0, 0);

INSERT INTO uniqueid VALUES (1, 1, 'movie', 'tt0113277', 'imdb', 1);
INSERT INTO uniqueid VALUES (2, 1, 'movie', '949', 'tmdb', 0);
INSERT INTO uniqueid VALUES (3, 2, 'movie', '603', 'TMDB', 1);
INSERT INTO uniqueid VALUES (4, 1, 'set', '2344', 'tmdb', 0);

INSERT INTO genre VALUES (1, 'Krimi');
INSERT INTO genre VALUES (2, 'Drama');
INSERT INTO genre VALUES (3, 'Science Fiction');
INSERT INTO genrelinkmovie VALUES (2, 1, 1);
INSERT INTO genrelinkmovie VALUES (1, 1, 0);
INSERT INTO genrelinkmovie VALUES (3, 2, 0);
INSERT INTO genrelinkmovie VALUES (3, 3, 0);

INSERT INTO actors VALUES (1, 'Al Pacino', 'https://image.tmdb.org/t/p/original/pacino.jpg');
INSERT INTO actors VALUES (2, 'Robert De Niro', '');
INSERT INTO actors VALUES (3, 'Michael Mann', '');
INSERT INTO actors VALUES (4, 'Keanu Reeves', '');
INSERT INTO actors VALUES (5, 'Lana Wachowski', '');
INSERT INTO actorlinkmovie VALUES (2, 1, 'Neil McCauley', 1);
INSERT INTO actorlinkmovie VALUES (1, 1, 'Lt. Vincent Hanna', 0);
INSERT INTO actorlinkmovie VALUES (4, 2, 'Neo', 0);
INSERT INTO actorlinkmovie VALUES (4, 3, '', 0);
INSERT INTO directorlinkmovie VALUES (3, 1);
INSERT INTO directorlinkmovie VALUES (5, 2);
INSERT INTO writerlinkmovie VALUES (3, 1);
INSERT INTO writerlinkmovie VALUES (5, 2);

INSERT INTO studio VALUES (1, 'Warner Bros.');
INSERT INTO studio VALUES (2, 'Regency');
INSERT INTO studiolinkmovie VALUES (2, 1);
INSERT INTO studiolinkmovie VALUES (1, 1);
INSERT INTO studiolinkmovie VALUES (1, 2);
INSERT INTO country VALUES (1, 'USA');
INSERT INTO countrylinkmovie VALUES (1, 1);
INSERT INTO tag VALUES (1, 'Heist');
INSERT INTO taglinks VALUES (1, 1, 'movie', 0);
INSERT INTO taglinks VALUES (1, 1, 'tvshow', 0);

INSERT INTO rating VALUES (1, 1, 'movie', 'imdb', 10, 8.3, 650000, 1);
INSERT INTO rating VALUES (2, 1, 'movie', 'themoviedb', 10, 7.9, 1000, 0);
INSERT INTO rating VALUES (3, 1, 'movie', 'trakt', 10, 0, 0, 0);
INSERT INTO rating VALUES (4, 2, 'movie', 'imdb', 10, 8.7, NULL, 1);
INSERT INTO rating VALUES (5, 1, 'tvshow', 'imdb', 10, 9.9, 1, 1);

INSERT INTO sets VALUES (1, 'Matrix Collection', 'Neo gegen die Maschinen.', '');
INSERT INTO setlinkmovie VALUES (1, 2, 0);
INSERT INTO setlinkmovie VALUES (1, 3, -1);

INSERT INTO ExcludeDir VALUES ('D:\Filme\Privat');

INSERT INTO tvshow VALUES (1, 1, '\\nas\serien\Lost');
INSERT INTO episode VALUES (1, 1, 'Pilot (1)');
INSERT INTO episode VALUES (2, 1, 'Pilot (2)');
