-- Weitere Codec-Namen werden übersetzt; betroffene Einträge beim Start neu indizieren.
UPDATE movie SET audio_codecs = NULL WHERE video_codec LIKE 'v!_%' ESCAPE '!';
UPDATE episode SET audio_codecs = NULL WHERE video_codec LIKE 'v!_%' ESCAPE '!';
