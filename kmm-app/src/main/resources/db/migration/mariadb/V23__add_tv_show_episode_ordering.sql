-- Episode order chosen for the TV show, e.g. "tvdb:alternate"; without a value the one from the settings applies.
ALTER TABLE tv_show
    ADD COLUMN episode_ordering VARCHAR(120);

ALTER TABLE tv_show
    ADD COLUMN episode_ordering_name VARCHAR(255);
