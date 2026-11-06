package de.tiktak.kmm.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.json.JsonMapper;

class ExportWriterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);
    private static final List<ExportTable.Column> COLUMNS = List.of(new ExportTable.Column("title", "Titel"),
            new ExportTable.Column("rating", "Bewertung"), new ExportTable.Column("genres", "Genres"),
            new ExportTable.Column("watched", "Gesehen"), new ExportTable.Column("added", "Hinzugefügt"));

    private final ExportWriter writer = new ExportWriter(JsonMapper.builder().build(), new PosterThumbnails());

    @TempDir
    Path directory;

    @Test
    void writesCsvForGermanExcel() throws IOException {
        String csv = new String(write(table(null), ExportFormat.CSV), StandardCharsets.UTF_8);

        assertThat(csv).startsWith("﻿Titel;Bewertung;Genres;Gesehen;Hinzugefügt\r\n")
                .contains("\"Heat; \"\"Director's Cut\"\"\";8,3;Krimi, Drama;ja;2026-10-29\r\n")
                .contains("Die <Märchen> & Co;;;nein;\r\n");
    }

    @Test
    void writesCsvForEnglishExcel() throws IOException {
        String csv = new String(write(table(Locale.UK, null, null), ExportFormat.CSV), StandardCharsets.UTF_8);

        assertThat(csv).startsWith("\uFEFFTitel,Bewertung,Genres,Gesehen,Hinzugefügt\r\n")
                .contains("\"Heat; \"\"Director's Cut\"\"\",8.3,\"Krimi, Drama\",yes,2026-10-29\r\n")
                .contains("Die <Märchen> & Co,,,no,\r\n");
    }

    @Test
    void choosesTheSeparatorExcelExpectsForTheLanguage() {
        assertThat(ExportWriter.csvSeparator(Locale.GERMANY)).isEqualTo(';');
        assertThat(ExportWriter.csvSeparator(Locale.ENGLISH)).isEqualTo(',');
        assertThat(ExportWriter.csvSeparator(Locale.FRENCH)).isEqualTo(',');
    }

    @Test
    void writesJsonWithKeysAndTypes() throws IOException {
        String json = new String(write(table(null), ExportFormat.JSON), StandardCharsets.UTF_8);

        assertThat(JsonMapper.builder().build().readTree(json).toString()).isEqualTo(
                "[{\"id\":1,\"title\":\"Heat; \\\"Director's Cut\\\"\",\"rating\":8.3,\"genres\":[\"Krimi\",\"Drama\"],"
                        + "\"watched\":true,\"added\":\"2026-10-29\"},"
                        + "{\"id\":2,\"title\":\"Die <Märchen> & Co\",\"rating\":null,\"genres\":[],"
                        + "\"watched\":false,\"added\":null}]");
    }

    @Test
    void writesAnHtmlListWithThumbnailsAsZip() throws IOException {
        Path poster = directory.resolve("poster.png");
        ImageIO.write(new BufferedImage(400, 600, BufferedImage.TYPE_INT_RGB), "png", poster.toFile());
        Path notAnImage = Files.writeString(directory.resolve("kaputt.jpg"), "kein Bild");

        Map<String, byte[]> zip = unzip(write(table(poster, notAnImage), ExportFormat.HTML));

        assertThat(zip).containsOnlyKeys("posters/1.jpg", "index.html");
        BufferedImage thumbnail = ImageIO.read(new ByteArrayInputStream(zip.get("posters/1.jpg")));
        assertThat(thumbnail.getWidth()).isEqualTo(PosterThumbnails.WIDTH);
        assertThat(thumbnail.getHeight()).isEqualTo(240);
        String html = new String(zip.get("index.html"), StandardCharsets.UTF_8);
        assertThat(html).contains("<title>Filme – Kodi Media Manager</title>", "exportiert am 29.10.2026",
                "<span id=\"count\">2</span> von 2", "<img loading=\"lazy\" alt=\"\" src=\"posters/1.jpg\">",
                "<td>Heat; &quot;Director&#39;s Cut&quot;</td>", "<td>Die &lt;Märchen&gt; &amp; Co</td>",
                "<td>8,3</td>").doesNotContain("<Märchen>");
    }

    @Test
    void writesTheHtmlListInEnglish() throws IOException {
        Map<String, byte[]> zip = unzip(write(table(Locale.UK, null, null), ExportFormat.HTML));

        String html = new String(zip.get("index.html"), StandardCharsets.UTF_8);
        assertThat(html).contains("<html lang=\"en\">", "<span id=\"count\">2</span> of 2 entries",
                "exported on 29 Oct 2026", "placeholder=\"Search …\"", "<td>8.3</td>", "<td>yes</td>");
    }

    private byte[] write(ExportTable table, ExportFormat format) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writer.write(table, format, TODAY, output);
        return output.toByteArray();
    }

    private static ExportTable table(Path firstPoster) {
        return table(firstPoster, null);
    }

    private static ExportTable table(Path firstPoster, Path secondPoster) {
        return table(Locale.GERMAN, firstPoster, secondPoster);
    }

    private static ExportTable table(Locale locale, Path firstPoster, Path secondPoster) {
        return new ExportTable("Filme", locale, COLUMNS, List.of(
                new ExportTable.Row(1, List.of("Heat; \"Director's Cut\"", 8.3, List.of("Krimi", "Drama"), true,
                        TODAY), firstPoster),
                new ExportTable.Row(2, Arrays.asList("Die <Märchen> & Co", null, List.of(), false, null),
                        secondPoster)));
    }

    private static Map<String, byte[]> unzip(byte[] bytes) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }
}
