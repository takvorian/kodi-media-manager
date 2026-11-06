package de.tiktak.kmm.scanner;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Part of a multi-part movie according to Kodi's convention, e.g. {@code Heat cd1.avi}, {@code Heat-part2.mkv}.
 *
 * @param stackKey   common key of all parts (folder + name without part suffix + extension)
 * @param baseName   file name without part suffix and extension
 * @param partNumber number of the part
 */
record StackedFile(String stackKey, String baseName, int partNumber) {

    private static final Pattern STACK_PART = Pattern.compile(
            "(?i)^(.*?)[ _.-]*(?:cd|dvd|p(?:ar)?t|dis[ck])[ _.-]*([0-9]+)(.*?)$");
    private static final int PREFIX_GROUP = 1;
    private static final int PART_GROUP = 2;
    private static final int SUFFIX_GROUP = 3;
    private static final char KEY_SEPARATOR = '|';

    static Optional<StackedFile> of(Path file, String nameWithoutExtension, String extension) {
        Matcher matcher = STACK_PART.matcher(nameWithoutExtension);
        if (!matcher.matches() || matcher.group(PREFIX_GROUP).isBlank()) {
            return Optional.empty();
        }
        String baseName = matcher.group(PREFIX_GROUP) + matcher.group(SUFFIX_GROUP);
        String stackKey = String.valueOf(file.getParent()) + KEY_SEPARATOR
                + baseName.toLowerCase(Locale.ROOT) + KEY_SEPARATOR + extension.toLowerCase(Locale.ROOT);
        return Optional.of(new StackedFile(stackKey, baseName, Integer.parseInt(matcher.group(PART_GROUP))));
    }
}
