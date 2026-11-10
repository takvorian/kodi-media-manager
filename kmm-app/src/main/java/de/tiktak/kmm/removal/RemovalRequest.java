package de.tiktak.kmm.removal;

/**
 * How to remove: from the library only, or the files as well.
 *
 * @param deleteFiles whether the files are deleted for good, without a recycle bin
 */
public record RemovalRequest(boolean deleteFiles) {
}
