package de.tiktak.kmm.i18n;

/**
 * Base class for exceptions whose message is shown to the user. {@link #getMessage()} is the English text for logs;
 * the user interface translates {@link #localizedText()}.
 */
public class LocalizedException extends RuntimeException implements Localized {

    private final transient LocalizedText text;

    public LocalizedException(LocalizedText text) {
        this(text, null);
    }

    public LocalizedException(LocalizedText text, Throwable cause) {
        super(Messages.english(text), cause);
        this.text = text;
    }

    @Override
    public LocalizedText localizedText() {
        return text;
    }
}
