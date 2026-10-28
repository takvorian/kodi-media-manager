package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tiktak.kmm.plugin.api.MetadataResult;

class ScrapedMetadataMergerTest {

    private static final MovieMetadata CURRENT = new MovieMetadata("Heat (alt)", "Heat OT", "Heat, The", 1994,
            null, "Alte Handlung", "Kurz", null, 160, "FSK 18", List.of("Action"), "tt0113277", null, null, null);

    @Test
    void replacesEverythingTheScraperDelivers() {
        MetadataResult scraped = MetadataResult.builder()
                .title("Heat").originalTitle("Heat").year(1995).premiered(LocalDate.of(1995, 12, 15))
                .plot("Neue Handlung").tagline("A Los Angeles Crime Saga").runtime(170).certification("FSK 16")
                .genres(List.of("Krimi", "Drama")).uniqueIds(Map.of("imdb", "tt0113277", "tmdb", "949"))
                .build();

        MovieMetadata merged = ScrapedMetadataMerger.merge(CURRENT, scraped);

        assertThat(merged).isEqualTo(new MovieMetadata("Heat", "Heat", "Heat, The", 1995, LocalDate.of(1995, 12, 15),
                "Neue Handlung", "Kurz", "A Los Angeles Crime Saga", 170, "FSK 16", List.of("Krimi", "Drama"),
                "tt0113277", "949", null, null));
    }

    @Test
    void keepsCurrentValuesTheScraperDoesNotKnow() {
        MetadataResult scraped = MetadataResult.builder().title("Heat").build();

        MovieMetadata merged = ScrapedMetadataMerger.merge(CURRENT, scraped);

        assertThat(merged.title()).isEqualTo("Heat");
        assertThat(merged).usingRecursiveComparison().ignoringFields("title").isEqualTo(CURRENT);
    }
}
