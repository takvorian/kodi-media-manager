ALTER TABLE movie
    ADD COLUMN original_title  VARCHAR(512)  NULL,
    ADD COLUMN sort_title      VARCHAR(512)  NULL,
    ADD COLUMN premiered       DATE          NULL,
    ADD COLUMN plot            LONGTEXT      NULL,
    ADD COLUMN outline         VARCHAR(2048) NULL,
    ADD COLUMN tagline         VARCHAR(512)  NULL,
    ADD COLUMN runtime_minutes INT           NULL,
    ADD COLUMN mpaa            VARCHAR(64)   NULL,
    ADD COLUMN imdb_id         VARCHAR(16)   NULL,
    ADD COLUMN tmdb_id         VARCHAR(16)   NULL,
    ADD COLUMN nfo_modified_at DATETIME(6)   NULL;

CREATE TABLE movie_genre (
    movie_id    BIGINT       NOT NULL,
    genre_order INT          NOT NULL,
    genre       VARCHAR(128) NOT NULL,
    CONSTRAINT pk_movie_genre PRIMARY KEY (movie_id, genre_order),
    CONSTRAINT fk_movie_genre_movie FOREIGN KEY (movie_id) REFERENCES movie (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
