package de.tiktak.kmm.plugin.tmdb;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import de.tiktak.kmm.plugin.api.EpisodeOrder;
import de.tiktak.kmm.plugin.api.EpisodeResult;
import de.tiktak.kmm.plugin.api.SeasonResult;
import de.tiktak.kmm.plugin.api.TvShowResult;
import tools.jackson.databind.JsonNode;

/**
 * TMDB's episode groups: alternative orders of the episodes of a TV show, such as a streaming service's order. Every
 * group of an episode group becomes a season, its episodes are numbered in the order of the group.
 */
final class TmdbEpisodeGroups {

    /** The aired order, which TMDB has without an episode group. */
    static final String AIRED = "aired";
    static final String AIRED_NAME = "Original air date";
    private static final Pattern GROUP_ID = Pattern.compile("[0-9a-f]{24}");
    private static final Pattern SPECIALS = Pattern.compile(".*(special|extra|besonder|bonus).*");
    private static final Comparator<JsonNode> BY_ORDER = Comparator.comparingInt(node -> node.path("order").asInt());
    /** TMDB's types of episode groups. */
    private static final Map<Integer, String> TYPES = Map.of(1, "Original air date", 2, "Absolute", 3, "DVD",
            4, "Digital", 5, "Story arc", 6, "Production", 7, "TV");

    private TmdbEpisodeGroups() {
    }

    static boolean isGroupId(String id) {
        return GROUP_ID.matcher(id).matches();
    }

    /** The aired order first, then the episode groups, e.g. {@code Netflix Order (Digital)}; empty without groups. */
    static List<EpisodeOrder> orders(JsonNode groups) {
        List<EpisodeOrder> orders = new ArrayList<>();
        orders.add(new EpisodeOrder(AIRED, AIRED_NAME));
        JsonNodes.elements(groups.path("results"))
                .filter(group -> isGroupId(group.path("id").asString()))
                .map(group -> new EpisodeOrder(group.path("id").asString(), name(group)))
                .forEach(orders::add);
        return orders.size() == 1 ? List.of() : List.copyOf(orders);
    }

    /**
     * Numbers the episodes of the aired order as in the episode group; episodes outside the group are dropped. The
     * episodes keep their details, since the group only lists their ID and basic data.
     */
    static TvShowResult renumber(TvShowResult aired, JsonNode episodeGroup) {
        Map<String, EpisodeResult> byId = new HashMap<>();
        aired.episodes().forEach(episode -> byId.put(episode.uniqueIds().get(TmdbApi.TMDB), episode));
        List<JsonNode> groups = JsonNodes.elements(episodeGroup.path("groups")).sorted(BY_ORDER).toList();
        int offset = firstIsNoSpecials(groups) ? 1 : 0;
        List<SeasonResult> seasons = new ArrayList<>();
        List<EpisodeResult> episodes = new ArrayList<>();
        for (JsonNode group : groups) {
            int season = group.path("order").asInt() + offset;
            seasons.add(new SeasonResult(season, name(group, null), null, null));
            JsonNodes.elements(group.path("episodes")).sorted(BY_ORDER).forEach(entry -> {
                EpisodeResult episode = byId.get(entry.path("id").asString());
                if (episode != null) {
                    episodes.add(episode.toBuilder().season(season).episode(entry.path("order").asInt() + 1).build());
                }
            });
        }
        return aired.toBuilder().seasons(seasons).episodes(episodes).build();
    }

    /** Season 0 holds the specials; a group with order 0 that is no specials group starts with season 1. */
    private static boolean firstIsNoSpecials(List<JsonNode> groups) {
        return !groups.isEmpty() && groups.getFirst().path("order").asInt() == 0
                && !SPECIALS.matcher(groups.getFirst().path("name").asString().toLowerCase(Locale.ROOT)).matches();
    }

    private static String name(JsonNode group, String fallback) {
        String name = group.path("name").asString("").strip();
        return name.isEmpty() ? fallback : name;
    }

    private static String name(JsonNode group) {
        String name = name(group, group.path("id").asString());
        String type = TYPES.get(group.path("type").asInt());
        return type == null ? name : "%s (%s)".formatted(name, type);
    }
}
