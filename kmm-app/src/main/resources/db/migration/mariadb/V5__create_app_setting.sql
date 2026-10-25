CREATE TABLE app_setting (
    setting_key   VARCHAR(128) NOT NULL PRIMARY KEY,
    setting_value LONGTEXT     NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
