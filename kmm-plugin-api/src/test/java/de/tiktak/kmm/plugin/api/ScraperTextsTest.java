package de.tiktak.kmm.plugin.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.i18n.Messages;

class ScraperTextsTest {

    private static final String PROVIDER = "fanart.tv";

    @Test
    void namesTheProviderAndTheStatusOfAnHttpError() {
        LocalizedText text = ScraperTexts.httpError(PROVIDER, 503);

        assertThat(text).isEqualTo(LocalizedText.of("scraper.httpError").with("provider", PROVIDER)
                .with("status", 503));
        assertThat(Messages.english(text)).isEqualTo("fanart.tv responded with error 503.");
    }

    @Test
    void passesTheReasonOfAnUnreachableProviderOnAsIs() {
        LocalizedText text = ScraperTexts.unreachable(PROVIDER, "Connection refused");

        assertThat(text.args()).containsEntry("provider", PROVIDER).containsEntry("reason", "Connection refused");
        assertThat(Messages.english(text)).isEqualTo("fanart.tv cannot be reached: Connection refused");
    }

    @Test
    void rendersEveryTextWithTheProvider() {
        assertThat(Messages.english(ScraperTexts.interrupted(PROVIDER)))
                .isEqualTo("The request to fanart.tv was interrupted.");
        assertThat(Messages.english(ScraperTexts.unexpectedResponse(PROVIDER)))
                .isEqualTo("fanart.tv returned an unexpected response.");
        assertThat(Messages.english(ScraperTexts.apiKeyRejected(PROVIDER)))
                .isEqualTo("fanart.tv rejects the API key. Please check it under Settings → API keys.");
        assertThat(Messages.english(ScraperTexts.rateLimitReached(PROVIDER)))
                .isEqualTo("The request limit of fanart.tv has been reached. Please try again later.");
        assertThat(Messages.english(ScraperTexts.apiKeyMissing(PROVIDER)))
                .isEqualTo("An API key for fanart.tv is missing (Settings → API keys).");
    }
}
