-- Order of the scrapers of one kind for a source, e.g. "tmdb,-tvdb" (a leading "-" switches a scraper off);
-- a kind without a row follows the order from the settings.
CREATE TABLE media_source_scraper
(
    source_id     BIGINT        NOT NULL,
    kind          VARCHAR(20)   NOT NULL,
    scraper_order VARCHAR(1000) NOT NULL,
    CONSTRAINT pk_media_source_scraper PRIMARY KEY (source_id, kind),
    CONSTRAINT fk_media_source_scraper_source FOREIGN KEY (source_id) REFERENCES media_source (id) ON DELETE CASCADE
);
