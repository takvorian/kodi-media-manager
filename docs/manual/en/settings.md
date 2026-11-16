# Settings

Changes only take effect when you click **Save** at the bottom of the page. **Discard** restores the saved state. The scrapers have their own tab, see [Scrapers](settings.md#scrapers).

If a field says “Fixed by the configuration”, someone has set the value through an environment variable, and it is locked here.

## Interface language

The interface is available in German and English. You can switch at any time at the top right next to the **?** (**DE**/**EN**); the page does not reload, and the manual switches language too. KMM remembers the choice in the browser.

When the page opens, this order applies:

1. the `lang` parameter in the address, for example `http://nas:8080/?lang=en`; `en_GB`, `en-GB`, `de` or `de_DE` work as well,
2. the language last chosen in this browser,
3. otherwise English.

Dates and numbers follow the interface language: British English like `25/10/2026`, German like `25.10.2026`. The metadata language KMM requests from the providers is independent of this, see the next section.

## Language

Applies to movies if neither the movie nor its source has a language of its own, see [Language per entry](scraping.md#language-per-source-and-entry).

- **Metadata:** for example `de-DE` or `en-US`.
- **Preferred artwork language:** two letters, for example `de`. Artwork in this language appears first in the selection.

## TV shows

The same two fields for TV shows, plus the **Episode order**: aired, DVD/Blu-ray or absolute (without seasons). This applies to scraping and only affects TVDB. For single TV shows you choose another order in the TV show view, see [Episode order](tv-shows.md#episode-order).

## Technical details

- **ffprobe:** the path to ffprobe from FFmpeg, or just `ffprobe` if it is on the search path.
- **Read after auto-scraping**.
- **Read new files without details in the NFO after the scan**.

## Rename

The patterns and the automatic triggers, explained in detail under [Renaming](renaming.md).

## Movie sets

- **Assign movies to their movie set when scraping:** replaces another set of the movie.
- **Store set artwork in the flat scheme:** like Ember, as `<folder>/<set title>-poster.jpg`.
- **Store set artwork in Kodi’s set folders:** as `<folder>/<set title>/poster.jpg`. The folder must be the “Movie set information folder” configured in Kodi.

There is more on this in [Movie sets](movie-sets.md).

## Scan

- **Video file extensions:** separated by commas, for example `mkv, mp4, avi`.
- **Excluded folders:** one full path per line. The scan skips them together with their contents.

## Proxy

For the case that KMM can only reach the internet through a proxy: host, port and, if needed, user name and password. Kodi is never contacted through the proxy.

<a id="scrapers"></a>
## Scrapers

The settings have two tabs at the top: **General** with everything described so far and **Scrapers**. Under **Scrapers** you see each provider with what it delivers (metadata, artwork, ratings and details, theme music, trailers, movie sets) and for which media, and its state:

- **ready**: KMM uses it.
- **setting missing**: usually the key is missing; below it says what exactly. KMM skips the provider until you enter it.
- **switched off**: KMM does not ask it.

A click on a provider opens its page. There you switch it on or off with **Switched on** and change its settings under **Keys and options**. Then click **Save**.

<a id="scraper-order"></a>
### Order

Below the overview you set for **Movies** and **TV shows** in which order KMM asks the providers per kind. Move a provider with the arrows, switch it off for this kind with its tick box. Then click **Save**.

- **Metadata:** the first provider identifies new movies and TV shows. If it finds nothing, the next one tries. When fetching again, the first one set up applies.
- **Artwork:** KMM asks all ticked providers. Within one language the images of the provider further up come first, and the automatic choice takes the first image.
- **Ratings and details:** if both fill an empty field, the provider further up wins.
- **Theme music, trailers, movie sets:** in this order.

Without a choice of your own, TMDB comes first for movies and TVDB for TV shows, the others follow by name. Ratings have a fixed order. Single sources can differ, see [Scrapers of a source](sources.md#source-scrapers). Single movies and TV shows can have a scraper of their own, see [Scraper of the TV show](tv-shows.md#own-scraper) and [Scraper of the movie](movies.md#own-scraper).

<a id="api-keys"></a>
### Keys

| Provider | Key | Used for | Where to get it |
|---|---|---|---|
| TMDB | API key | movies, TV shows, artwork, trailers | <https://www.themoviedb.org/settings/api> |
| TVDB | project key, with a subscription also the PIN | TV shows, artwork | <https://thetvdb.com/api-information> |
| fanart.tv | project key, optionally a personal key | artwork | <https://fanart.tv/get-an-api-key/> |
| OMDb | API key | ratings, English details | <https://www.omdbapi.com/apikey.aspx> |
| Trakt | client ID | community rating | <https://trakt.tv/oauth/applications> |

KMM stores the keys in its database and only shows their last characters. An empty field leaves the stored key unchanged; **Remove** deletes it.

Environment variables such as `KMM_TMDB_API_KEY` only fill in a key that has not been stored yet, at startup. After that the stored key applies, and a deleted key stays deleted.

### Further options

- **TMDB, TVDB:** the country of the age rating, e.g. `DE` or `US`.
- **Moviepilot, OFDb:** the pause between two requests in milliseconds, so KMM does not strain the sites.
- **YouTube:** the yt-dlp program, the ffmpeg folder and the number of search results for theme music.

The default is shown below each field. If you empty a field, the default applies again. Values from environment variables such as `KMM_YTDLP` are this default.

## Environment variables

For movies, these variables take precedence over the fields above: `KMM_TMDB_LANGUAGE`, `KMM_ARTWORK_LANGUAGE`, `KMM_PROXY_HOST`, `KMM_PROXY_PORT`, `KMM_PROXY_USERNAME`, `KMM_PROXY_PASSWORD`. More are listed under [Installation](installation.md#access-from-your-home-network).
