# Introduction

Kodi Media Manager (KMM) looks after your movie and TV show library for [Kodi](https://kodi.tv): it finds your files, fetches details and artwork from the internet, stores them the way Kodi expects, and tells Kodi when something has changed. KMM is a successor to Ember Media Manager and can take over its data.

You use KMM in your browser. It runs on your computer or as a container on a server.

## What KMM can do

- **Library:** read movies, TV shows, seasons and episodes from your folders.
- **Online data:** fetch metadata, artwork, trailer links, theme music and ratings from several providers, for a single entry or a whole source.
- **Technical details:** read resolution, codecs, audio tracks and subtitles from your files with ffprobe.
- **Organizing:** rename files and folders using your own patterns, manage movie sets and tags.
- **Selection:** filter lists, save filters and edit many entries at once.
- **Kodi:** update several Kodi devices automatically and fetch the watched status from Kodi.
- **Export:** download filtered lists as HTML, CSV or JSON.

## How KMM handles your data

You should know this before you start:

- **NFO files:** KMM stores details in a database and also in NFO files next to your videos. Kodi reads these files, and they stay usable without KMM.
- **Saving writes to your media folders:** when you save a movie, add a trailer or apply an artwork, KMM changes files next to the video. The scan can write as well: for new files it adds the technical details to the NFO. This is on by default and [can be turned off](settings.md#technical-details).
- **Existing data stays:** automatic scraping only fills in what is missing. It never overwrites details or artwork that are already there.
- **Renaming can be turned off:** automatic renaming is off at first. Before renaming a whole source, you always see a preview.
- **Ember stays untouched:** the Ember import only reads your Ember profile.

Even so, back up your NFO files before big operations, especially before renaming a whole library for the first time.

## Where to go next

Start with [Installation and start](installation.md) and then continue with [Getting started](getting-started.md). If something does not work, see [Troubleshooting](troubleshooting.md).
