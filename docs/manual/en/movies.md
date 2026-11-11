# Movies

## The movie list

The **Movies** page shows all movies with title, year, format, date added and file. Clicking a column header sorts the list by that column; clicking it again reverses the order. With **Added**, for example, you see the newest movies first. The pagination bar sits above and below the list. At the top you can search by title and choose a [filter](filters.md). Use the checkboxes on the left to select movies for [batch actions](filters.md#batch-edit), and use **Export** to download the current list as an [export](export.md).

The list keeps its page, sorting, search and filter in the address. When you go from the movie view back to the list with **Back**, you are on the same page at the same position. Reloading and the browser's back button keep the list too, and you can bookmark a filtered list.

![The movie list](../images/movie-list.png)

## The movie view

Clicking a title opens the movie view. From top to bottom it shows:

- **File and NFO:** where the movie and its NFO are stored. If there is no NFO yet, KMM creates it when you save.
- **More details:** movie set, ratings, director, writer, studios, countries, tags, watched status and cast.
- **Technical details:** video, audio and subtitles.
- **Trailer**, **Theme music** and **Artwork**, see [Scraping](scraping.md).
- **Form:** the details you can edit directly.

![The movie view](../images/movie-view.png)

## Editing details

Change the fields in the form and click **Save**. KMM then writes the NFO and updates Kodi if [real-time sync](kodi.md#real-time-sync) is turned on. **Discard** restores the saved state.

Some fields in detail:

- **Sort title:** sets the order in Kodi, for example “Godfather, The” for “The Godfather”.
- **Edition:** for example “Director’s Cut”. Can be used in the file name and in filters.
- **Video source:** for example `bluray` or `dvd`. Can also be used in the file name, placeholder `{source}`.
- **Certification:** for example “FSK 16”.
- **IMDb ID:** `tt` followed by 7 to 10 digits. Several providers find the movie through it. Without it, Moviepilot, OFDb and OMDb add nothing.
- **TMDB ID:** digits only.
- **Genres:** separate several with commas.

## Buttons in the movie view

| Button | What happens |
|---|---|
| **Scan** | Reads the folder of this movie again, see [Scanning a movie](movies.md#scan). |
| **Refresh in Kodi** | Kodi reads this movie again right away. |
| **Watched status from Kodi** | Fetches play counts and your own rating from Kodi. |
| **Read technical details** | Reads the file with ffprobe. |
| **Rename** | Renames only this movie using the patterns from the settings. |
| **Scrape** | Fills in everything missing, for this movie only, see [Scraping](scraping.md#automatic-scraping). |
| **Search online** | Searches for the movie at a provider, see [Scraping](scraping.md#online-search). |
| **Remove** | Removes the movie, see [Removing](movies.md#remove). |

<a id="scan"></a>

## Scanning a movie

**Scan** in the movie view reads only the folder of this movie again, not the whole source. In the movie list you can scan several selected movies at once. The same happens as when [scanning a source](sources.md): new movies in the folder are added, movies without a file are removed from the library, and changed NFOs are taken over. Kodi devices with [real-time sync](kodi.md#real-time-sync) then look through the folder and remove entries whose files are gone.

**Replaced video file:** If you have exchanged the video file of a movie, for example turned `Movie.avi` and `Movie.srt` into a `Movie.mkv` with a built-in subtitle, KMM keeps the entry with all its details. This is how KMM recognizes the exchange: the new file belongs to the NFO of the old one. That is the case if it has the same name or if the movie has a folder of its own with a `movie.nfo`. The technical details still come from the old file. KMM therefore reads them again if reading after the scan is switched on in the [settings](settings.md#technical-details), otherwise use **Read technical details**. Kodi devices with real-time sync remove the old entry and read the movie again; watched status and rating come from the NFO. The same applies when the whole source is scanned.

If the folder cannot be reached or no longer contains any video files, KMM reports this and removes nothing. You remove a movie that no longer exists with [Removing](movies.md#remove).

<a id="technical-details"></a>

## Technical details

The **Technical details** card shows codec, resolution, aspect ratio, runtime, HDR, audio tracks and subtitles. The details come from the NFO or from ffprobe. For ffprobe, FFmpeg must be installed, see [Installation](installation.md#additional-programs). You can read them

- individually with **Read technical details**,
- for a whole source under [Sources](sources.md),
- for all episodes of a season in the TV show view, see [Scraping a season](tv-shows.md#scraping-a-season),
- and automatically after scraping and after scanning new files, see [Settings](settings.md#technical-details).

When writing, KMM only replaces this technical part of the NFO; the rest of the file stays unchanged.

<a id="remove"></a>

## Removing

You remove a movie with **Remove** in the movie view, several movies by ticking them in the movie list and clicking **Remove selection**. KMM first shows the affected movies and asks how to remove them:

- **From the library only** (default): the movies disappear from KMM and, with [real-time sync](kodi.md#real-time-sync) turned on, from Kodi. The files stay where they are, and KMM remembers nothing. The next scan of the source adds them again.
- **Delete the files as well:** KMM also deletes the files for good, without a recycle bin. The confirmation lists every file, and the button then reads **Delete for good**.

What is deleted:

- the movie file, for multi-part movies (`-cd1`, `-part2` …) all parts,
- everything that carries the name of the movie followed by a dot, hyphen, underscore or space: NFO, artwork, subtitles, trailers,
- for a movie in a folder of its own also `movie.nfo`; the folder itself goes if nothing is left in it. Otherwise a note says how many other files keep it,
- for DVD and Blu-ray folders (`VIDEO_TS`, `BDMV`) the whole movie folder.

If several movies share a folder, KMM deletes only the files of the chosen movie. It cleans up empty folders above it up to the source. KMM never deletes the source itself or anything outside it. A file that cannot be deleted is listed in the final message; the other files and the entries are gone nevertheless. A movie set stays, even if it has no movies left.

## Watched status

KMM fetches the status from Kodi, not the other way round. It only adds higher play counts, more recent dates or a rating, and never resets anything.
