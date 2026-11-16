-- Scraper chosen for the TV show or movie, e.g. "tmdb"; without a value the order of its source applies.
ALTER TABLE tv_show
    ADD COLUMN scraper_id VARCHAR(40);

ALTER TABLE movie
    ADD COLUMN scraper_id VARCHAR(40);
