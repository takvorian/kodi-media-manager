-- Eigene, minimale Nachbildung des Ember-Schemas v48 (nur die von KMM gelesenen Tabellen und Spalten).
CREATE TABLE moviesource (idSource INTEGER PRIMARY KEY, strName TEXT, strPath TEXT);
CREATE TABLE tvshowsource (idSource INTEGER PRIMARY KEY, strName TEXT, strPath TEXT);
CREATE TABLE movie (idMovie INTEGER PRIMARY KEY, idSource INTEGER, MoviePath TEXT, Imdb TEXT);
CREATE TABLE sets (idSet INTEGER PRIMARY KEY, Title TEXT);
CREATE TABLE tvshow (idShow INTEGER PRIMARY KEY, idSource INTEGER, TVShowPath TEXT);
CREATE TABLE episode (idEpisode INTEGER PRIMARY KEY, idShow INTEGER, Title TEXT);

INSERT INTO moviesource VALUES (1, 'Filme', 'D:\Filme');
INSERT INTO tvshowsource VALUES (1, 'Serien', '\\nas\serien');
INSERT INTO movie VALUES (1, 1, 'D:\Filme\Heat (1995)\Heat.mkv', 'tt0113277');
INSERT INTO movie VALUES (2, 1, 'D:\Filme\The Matrix (1999)\The Matrix.mkv', 'tt0133093');
INSERT INTO movie VALUES (3, 1, 'D:\Filme\The Matrix Reloaded (2003)\The Matrix Reloaded.mkv', 'tt0234215');
INSERT INTO sets VALUES (1, 'Matrix Collection');
INSERT INTO tvshow VALUES (1, 1, '\\nas\serien\Lost');
INSERT INTO episode VALUES (1, 1, 'Pilot (1)');
INSERT INTO episode VALUES (2, 1, 'Pilot (2)');
