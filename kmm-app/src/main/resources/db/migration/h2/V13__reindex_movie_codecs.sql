-- Codec-Namen aus Ember werden beim Start übersetzt und neu indiziert (MovieStreamIndexBackfill).
UPDATE movie SET audio_codecs = NULL WHERE stream_details IS NOT NULL;
