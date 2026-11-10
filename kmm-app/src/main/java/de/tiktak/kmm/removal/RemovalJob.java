package de.tiktak.kmm.removal;

import java.util.List;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.scanner.removal.Deletion;

/**
 * The steps every removal job takes: first the files if requested, then the library entries. Telling Kodi follows the
 * removal of the entries.
 */
public final class RemovalJob {

    /** Argument of the job titles that tells whether files are deleted. */
    public static final String MODE_ARGUMENT = "mode";
    private static final String FILES_MODE = "files";
    private static final String LIBRARY_MODE = "library";
    private static final LocalizedText DELETING_FILES = LocalizedText.of("removal.progress.files");
    private static final LocalizedText REMOVING_ENTRIES = LocalizedText.of("removal.progress.library");
    private static final int STEPS = 2;

    private RemovalJob() {
    }

    /**
     * Rejects deleting files that must not be deleted, before the job starts.
     *
     * @throws RemovalProblemException if files are to be deleted but lie outside the source or cannot be read
     */
    public static void requireAllowed(RemovalFiles files, boolean deleteFiles) {
        if (deleteFiles && !files.canDelete()) {
            throw RemovalProblemException.filesBlocked();
        }
    }

    /** The job title with the mode, so it says whether files are deleted. */
    public static LocalizedText title(LocalizedText title, boolean deleteFiles) {
        return title.with(MODE_ARGUMENT, deleteFiles ? FILES_MODE : LIBRARY_MODE);
    }

    /**
     * @param entries       number of entries that disappear from the library
     * @param removeEntries removes the entries from the library
     */
    public static RemovalResult run(RemovalFiles files, boolean deleteFiles, int entries, Runnable removeEntries,
                                    JobContext context) {
        Deletion deletion = new Deletion(List.of(), List.of());
        if (deleteFiles) {
            context.reportProgress(0, STEPS, DELETING_FILES);
            deletion = files.delete();
        }
        context.reportProgress(1, STEPS, REMOVING_ENTRIES);
        removeEntries.run();
        context.reportProgress(STEPS, STEPS);
        return new RemovalResult(entries, deletion.deleted().size(), deletion.failures());
    }
}
