ALTER TABLE movie
    ADD COLUMN video_resolution   INT          NULL,
    ADD COLUMN video_codec        VARCHAR(32)  NULL,
    ADD COLUMN hdr_type           VARCHAR(32)  NULL,
    ADD COLUMN audio_codecs       VARCHAR(512) NULL,
    ADD COLUMN audio_languages    VARCHAR(512) NULL,
    ADD COLUMN subtitle_languages VARCHAR(512) NULL;
