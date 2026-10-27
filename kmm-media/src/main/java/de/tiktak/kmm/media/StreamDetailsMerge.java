package de.tiktak.kmm.media;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BinaryOperator;

import de.tiktak.kmm.nfo.StreamDetails;

/**
 * Übernimmt neu ausgelesene Stream-Details, behält aber Angaben, die ffprobe nicht liefert: Sprachen
 * (wie Embers „LockLanguage“), den Bildaufbau (bei AVI kennt ffprobe ihn nicht) und Embers 3D-Angaben. Zugeordnet wird je Spurart nach Position; externe
 * Untertitel werden nur mit externen verglichen.
 */
public final class StreamDetailsMerge {

    private StreamDetailsMerge() {
    }

    public static StreamDetails keepKnown(StreamDetails probed, StreamDetails existing) {
        List<StreamDetails.Subtitle> embedded = merged(ofKind(probed.subtitles(), false),
                ofKind(existing.subtitles(), false), StreamDetailsMerge::subtitle);
        List<StreamDetails.Subtitle> external = merged(ofKind(probed.subtitles(), true),
                ofKind(existing.subtitles(), true), StreamDetailsMerge::subtitle);
        List<StreamDetails.Subtitle> subtitles = new ArrayList<>(embedded);
        subtitles.addAll(external);
        return new StreamDetails(
                merged(probed.videos(), existing.videos(), StreamDetailsMerge::video),
                merged(probed.audios(), existing.audios(), StreamDetailsMerge::audio),
                subtitles);
    }

    private static <T> List<T> merged(List<T> probed, List<T> existing, BinaryOperator<T> merge) {
        List<T> result = new ArrayList<>(probed.size());
        for (int index = 0; index < probed.size(); index++) {
            result.add(index < existing.size() ? merge.apply(probed.get(index), existing.get(index))
                    : probed.get(index));
        }
        return result;
    }

    private static List<StreamDetails.Subtitle> ofKind(List<StreamDetails.Subtitle> subtitles, boolean external) {
        return subtitles.stream().filter(subtitle -> subtitle.isExternal() == external).toList();
    }

    private static StreamDetails.Video video(StreamDetails.Video probed, StreamDetails.Video existing) {
        return new StreamDetails.Video(probed.codec(), probed.aspect(), probed.width(), probed.height(),
                probed.durationSeconds(), probed.bitrate(), orElse(probed.scanType(), existing.scanType()),
                orElse(probed.stereoMode(), existing.stereoMode()), orElse(probed.language(), existing.language()),
                probed.hdrType(), orElse(probed.multiViewCount(), existing.multiViewCount()),
                orElse(probed.multiViewLayout(), existing.multiViewLayout()), probed.fileSize());
    }

    private static StreamDetails.Audio audio(StreamDetails.Audio probed, StreamDetails.Audio existing) {
        return new StreamDetails.Audio(probed.codec(), orElse(probed.language(), existing.language()),
                probed.channels(), probed.bitrate());
    }

    private static StreamDetails.Subtitle subtitle(StreamDetails.Subtitle probed, StreamDetails.Subtitle existing) {
        return new StreamDetails.Subtitle(orElse(probed.language(), existing.language()), probed.forced(),
                probed.path());
    }

    private static String orElse(String value, String fallback) {
        return value != null ? value : fallback;
    }
}
