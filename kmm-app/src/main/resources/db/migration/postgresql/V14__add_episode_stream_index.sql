ALTER TABLE episode ADD COLUMN video_resolution INTEGER;
ALTER TABLE episode ADD COLUMN video_codec VARCHAR(32);
ALTER TABLE episode ADD COLUMN hdr_type VARCHAR(32);
ALTER TABLE episode ADD COLUMN audio_codecs VARCHAR(512);
ALTER TABLE episode ADD COLUMN audio_languages VARCHAR(512);
ALTER TABLE episode ADD COLUMN subtitle_languages VARCHAR(512);
