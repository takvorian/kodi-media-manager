CREATE TABLE movie_set (
    id                 BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name               VARCHAR(512) NOT NULL,
    overview           LONGTEXT     NULL,
    tmdb_collection_id VARCHAR(16)  NULL,
    CONSTRAINT uk_movie_set_name UNIQUE (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

ALTER TABLE movie
    ADD COLUMN movie_set_id     BIGINT      NULL,
    ADD COLUMN set_order        INT         NULL,
    ADD COLUMN user_rating      INT         NULL,
    ADD COLUMN play_count       INT         NULL,
    ADD COLUMN last_played_at   DATETIME(6) NULL,
    ADD COLUMN supplement_known BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT fk_movie_movie_set FOREIGN KEY (movie_set_id) REFERENCES movie_set (id) ON DELETE SET NULL,
    ADD INDEX ix_movie_movie_set (movie_set_id);

-- Bekannte Filme beim nächsten Scan neu einlesen, damit die zusätzlichen Angaben aus den NFOs ankommen.
UPDATE movie SET nfo_modified_at = NULL;

CREATE TABLE movie_credit (
    movie_id     BIGINT        NOT NULL,
    credit_order INT           NOT NULL,
    credit_type  VARCHAR(16)   NOT NULL,
    name         VARCHAR(512)  NOT NULL,
    role         VARCHAR(512)  NULL,
    thumb        VARCHAR(1024) NULL,
    CONSTRAINT pk_movie_credit PRIMARY KEY (movie_id, credit_order),
    CONSTRAINT fk_movie_credit_movie FOREIGN KEY (movie_id) REFERENCES movie (id) ON DELETE CASCADE,
    INDEX ix_movie_credit_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE movie_rating (
    movie_id       BIGINT      NOT NULL,
    rating_order   INT         NOT NULL,
    source         VARCHAR(64) NOT NULL,
    rating_value   DOUBLE      NOT NULL,
    max_value      INT         NULL,
    votes          INT         NULL,
    default_rating BOOLEAN     NOT NULL,
    CONSTRAINT pk_movie_rating PRIMARY KEY (movie_id, rating_order),
    CONSTRAINT fk_movie_rating_movie FOREIGN KEY (movie_id) REFERENCES movie (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE movie_studio (
    movie_id     BIGINT       NOT NULL,
    studio_order INT          NOT NULL,
    studio       VARCHAR(255) NOT NULL,
    CONSTRAINT pk_movie_studio PRIMARY KEY (movie_id, studio_order),
    CONSTRAINT fk_movie_studio_movie FOREIGN KEY (movie_id) REFERENCES movie (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE movie_country (
    movie_id      BIGINT       NOT NULL,
    country_order INT          NOT NULL,
    country       VARCHAR(255) NOT NULL,
    CONSTRAINT pk_movie_country PRIMARY KEY (movie_id, country_order),
    CONSTRAINT fk_movie_country_movie FOREIGN KEY (movie_id) REFERENCES movie (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE movie_tag (
    movie_id  BIGINT       NOT NULL,
    tag_order INT          NOT NULL,
    tag       VARCHAR(255) NOT NULL,
    CONSTRAINT pk_movie_tag PRIMARY KEY (movie_id, tag_order),
    CONSTRAINT fk_movie_tag_movie FOREIGN KEY (movie_id) REFERENCES movie (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
