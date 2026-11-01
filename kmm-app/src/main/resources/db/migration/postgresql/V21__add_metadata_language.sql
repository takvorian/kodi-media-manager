ALTER TABLE media_source
    ADD COLUMN metadata_language VARCHAR(16);

ALTER TABLE movie
    ADD COLUMN metadata_language VARCHAR(16);

ALTER TABLE tv_show
    ADD COLUMN metadata_language VARCHAR(16);
