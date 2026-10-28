CREATE TABLE saved_filter (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    media_type VARCHAR(16)  NOT NULL,
    name       VARCHAR(255) NOT NULL,
    definition LONGTEXT     NOT NULL,
    CONSTRAINT uk_saved_filter_name UNIQUE (media_type, name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
