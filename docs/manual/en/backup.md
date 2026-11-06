# Backup

The **Backup** page saves all KMM data to one file and restores it. This protects you against mistakes and lets you move KMM, even to a different database, for example from Windows to a NAS with MariaDB.

The backup includes sources, movies, TV shows, episodes, movie sets, filters, Kodi devices and settings. **Media, NFOs and artwork are not included.** They live in your media folders and must be backed up separately. Restoring changes nothing there and does not contact Kodi.

While a scan, import or other job is running, export and import are not possible. Wait until it has finished.

## Exporting

**Download backup** saves the file `kmm-sicherung-<date>.json.gz`.

**Include API keys and passwords** adds the API keys, the proxy password and the passwords of the Kodi devices. By default they are left out.

> **Caution:** if you tick this box, these credentials are stored in plain text in the file. Keep it safe and do not pass it on.

## Importing

1. Click **Choose backup**. KMM shows when and with which version the file was created and what it contains. Nothing is changed yet.
2. If needed, enter the **paths**, see below, and choose the **kind of import**.
3. Click **Dry run**. It runs everything and then undoes it, so the report shows exactly what would happen.
4. If everything looks right, click **Restore**.

### Replace or merge

**Replace** restores exactly the state of the file and deletes everything else. To confirm, you type the word `REPLACE`.

**Merge** keeps whatever exists only in KMM. If an entry matches, **the file wins**: its details and lists such as cast and genres are replaced with those from the file. The settings from the file also overwrite the existing ones.

| Entry | counts as the same if |
|---|---|
| Source | path |
| Movie | file path |
| TV show | folder path |
| Episode | file path, season and episode |
| Movie set, Kodi device | name |
| Filter | kind and name |
| Setting | key |

Names also count as the same if they differ only in upper/lower case or accents. If a source overlaps with another one or its name is already taken, KMM skips it together with its contents and lists it in the report.

### Paths when moving

If your media are now somewhere else as KMM sees them, enter the new path. Under Docker this is the path **in the container**, for example `/media/filme`, not the path on the NAS.

The mapping applies to sources, movies, TV shows, episodes, the local paths of the Kodi devices, excluded folders and the folders for set artwork. Paths that no rule matches stay unchanged and are counted in the report. If a source cannot be reached, the report shows a note.

### Credentials

If the file contains no credentials, KMM keeps the existing ones, even when replacing. Kodi passwords are matched by device name. If the file does contain credentials, they replace the existing ones.

## If something goes wrong

The import runs as a single unit. If it fails or you cancel it, everything stays as it was.

Before every real import, KMM saves the previous state as `backup/vor-import-<time>.json.gz` in the KMM data folder and keeps the last three. Under Docker the folder is in `/config`. The report gives the exact path. **These files contain the credentials.** To undo an import, download the file and restore it with **Replace**.

A backup from a newer KMM version can only be restored after you have updated KMM. Older backups work.
