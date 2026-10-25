CREATE TABLE kodi_host (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(64)  NOT NULL,
    address       VARCHAR(255) NOT NULL,
    port          INT          NOT NULL,
    username      VARCHAR(128) NULL,
    password      VARCHAR(255) NULL,
    realtime_sync BOOLEAN      NOT NULL,
    CONSTRAINT uk_kodi_host_name UNIQUE (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE kodi_host_path_mapping (
    kodi_host_id  BIGINT        NOT NULL,
    mapping_order INT           NOT NULL,
    local_path    VARCHAR(1024) NOT NULL,
    kodi_path     VARCHAR(1024) NOT NULL,
    CONSTRAINT pk_kodi_host_path_mapping PRIMARY KEY (kodi_host_id, mapping_order),
    CONSTRAINT fk_kodi_host_path_mapping_host FOREIGN KEY (kodi_host_id) REFERENCES kodi_host (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
