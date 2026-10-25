ALTER TABLE movie ADD COLUMN original_title VARCHAR(512);
ALTER TABLE movie ADD COLUMN sort_title VARCHAR(512);
ALTER TABLE movie ADD COLUMN premiered DATE;
ALTER TABLE movie ADD COLUMN plot TEXT;
ALTER TABLE movie ADD COLUMN outline VARCHAR(2048);
ALTER TABLE movie ADD COLUMN tagline VARCHAR(512);
ALTER TABLE movie ADD COLUMN runtime_minutes INTEGER;
ALTER TABLE movie ADD COLUMN mpaa VARCHAR(64);
ALTER TABLE movie ADD COLUMN imdb_id VARCHAR(16);
ALTER TABLE movie ADD COLUMN tmdb_id VARCHAR(16);
ALTER TABLE movie ADD COLUMN nfo_modified_at TIMESTAMP(6) WITH TIME ZONE;

CREATE TABLE movie_genre (
    movie_id    BIGINT       NOT NULL,
    genre_order INTEGER      NOT NULL,
    genre       VARCHAR(128) NOT NULL,
    CONSTRAINT pk_movie_genre PRIMARY KEY (movie_id, genre_order),
    CONSTRAINT fk_movie_genre_movie FOREIGN KEY (movie_id) REFERENCES movie (id) ON DELETE CASCADE
);
