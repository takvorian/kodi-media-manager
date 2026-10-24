CREATE TABLE media_source (
    id         BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(255)  NOT NULL,
    path       VARCHAR(1024) NOT NULL,
    media_type VARCHAR(32)   NOT NULL,
    created_at DATETIME(6)   NOT NULL,
    CONSTRAINT uk_media_source_name UNIQUE (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
