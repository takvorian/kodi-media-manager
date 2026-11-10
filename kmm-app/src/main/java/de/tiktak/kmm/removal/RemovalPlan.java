package de.tiktak.kmm.removal;

import java.util.List;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Preview of a removal; nothing has been changed yet.
 *
 * @param entries        every entry that disappears from the library, e.g. all episodes of a multi-episode file
 * @param files          absolute paths of the files that are deleted when files are deleted as well, sorted
 * @param directories    folders that are removed along with them, sorted
 * @param notes          hints such as multi-episode files, unknown files in the folder or missing files
 * @param canDeleteFiles whether the files may be deleted; if not, the notes say why
 */
public record RemovalPlan(List<LocalizedText> entries, List<String> files, List<String> directories,
                          List<LocalizedText> notes, boolean canDeleteFiles) {

    public RemovalPlan {
        entries = List.copyOf(entries);
        files = List.copyOf(files);
        directories = List.copyOf(directories);
        notes = List.copyOf(notes);
    }
}
