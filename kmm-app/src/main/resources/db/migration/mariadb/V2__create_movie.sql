ALTER TABLE media_source ADD COLUMN last_scanned_at DATETIME(6) NULL;

-- Eindeutigkeit über den SHA-256 des Pfads: ein Index über VARCHAR(1024) utf8mb4 überschreitet das InnoDB-Limit.
CREATE TABLE movie (
    id             BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_id      BIGINT        NOT NULL,
    file_path      VARCHAR(1024) NOT NULL,
    file_path_hash CHAR(64)      NOT NULL,
    title          VARCHAR(512)  NOT NULL,
    release_year   INT           NULL,
    disc_format    VARCHAR(16)   NOT NULL,
    part_count     INT           NOT NULL,
    added_at       DATETIME(6)   NOT NULL,
    CONSTRAINT uk_movie_file_path_hash UNIQUE (file_path_hash),
    CONSTRAINT fk_movie_source FOREIGN KEY (source_id) REFERENCES media_source (id) ON DELETE CASCADE,
    INDEX ix_movie_title (title)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
