# Installation and start

KMM is available as a Windows installer and as a Docker container.

## Windows installer

1. Run the file `Kodi Media Manager-<version>.msi`. It is not signed, so Windows SmartScreen shows a warning. Choose “More info” and then “Run anyway”.
2. Accept the license and choose a different installation folder if you like. You do not need administrator rights; KMM is installed for your user only.
3. Start KMM from the desktop shortcut or the Start menu.

A console window with the log opens. Wait until it shows `Started KodiMediaManagerApplication`, then open <http://localhost:8080> in your browser.

**Stopping:** close the console window.

**Updating:** simply install the new MSI; it replaces the old version.

**Uninstalling:** in the Windows settings under “Apps”. Your data is kept.

### Where your data is stored

The database and plugins are stored under `%USERPROFILE%\.kodi-media-manager`. Your media, NFOs and artwork stay in your media folders.

### Access from your home network

After installation, KMM can only be reached from your own computer and does not ask you to log in. You can change this with environment variables that you set before starting KMM:

| Variable | Meaning |
|---|---|
| `KMM_BIND_ADDRESS=0.0.0.0` | reachable from other devices |
| `KMM_SECURITY_ENABLED=true` | turn on login |
| `SPRING_SECURITY_USER_NAME` | user name |
| `SPRING_SECURITY_USER_PASSWORD` | password |
| `KMM_PORT` | a different port instead of 8080 |
| `KMM_HOME` | a different data folder |

**Never** make KMM reachable on the network **without login**, otherwise anyone on the network can change your media folders.

<a id="login"></a>
### Signing in

With login turned on, KMM shows its own sign-in page with user name and password. After signing in you return to the page you wanted to open.

- Without **Stay signed in**, the login lasts until you close the browser or KMM restarts.
- With **Stay signed in**, you stay signed in on this device for 30 days, even across a restart or an update of KMM. If you change the password, you have to sign in again everywhere.
- **Sign out** at the top right ends the login on this device.

Scripts and programs can still call the API with user name and password (HTTP Basic), for example `curl -u admin:password http://<server>:8080/api/v1/movies`.

### Additional programs

Two features need programs that are not included in the installer:

- **FFmpeg** for the [technical details](movies.md#technical-details): for example with `winget install Gyan.FFmpeg`. If `ffprobe` is not on the search path, enter its path under [Settings](settings.md#technical-details).
- **yt-dlp** for [theme music from YouTube](scraping.md#theme-music): for example with `winget install yt-dlp.yt-dlp`. If it is not on the search path, set `KMM_YTDLP` to the program and `KMM_FFMPEG_LOCATION` to the FFmpeg folder.

## Docker

The Docker image already includes FFmpeg and yt-dlp. There, KMM runs with a MariaDB database and always requires a [login](installation.md#login).

```bash
cd docker
cp .env.example .env     # enter passwords and directories
docker compose pull
docker compose up -d
```

The interface is then available at `http://<server>:8080`. The user name and password are in the `.env` file (`KMM_ADMIN_USER`, `KMM_ADMIN_PASSWORD`).

| Directory in the container | Setting in `.env` | Purpose |
|---|---|---|
| `/config` | `KMM_CONFIG_DIR` | plugins, cache, logs |
| `/media` | `KMM_MEDIA_DIR` | your media |

`PUID` and `PGID` set the user KMM runs as and uses to access your media. Enter the owner of your media files here; `ls -ln` on the server shows it. This user needs write access in `/media`, otherwise neither NFOs nor artwork can be saved. KMM hands the `/config` directory over to this user itself at startup.

**Updating:** `docker compose pull` followed by `docker compose up -d`. Your data is kept.

**Moving:** you take everything over from another KMM installation with a [backup](backup.md), even from Windows to a NAS.

**Fixed version:** without further settings, `pull` fetches the latest version. Enter, for example, `KMM_IMAGE_TAG=0.1` in the `.env` file to stay on a version or to go back to an earlier one. Going back only works if the database has not yet been changed by a newer version, so back it up before updates.

## Checking the version

The current version is shown in the header next to the title; clicking it opens the **System** page. It also lists the **Changes** of each version. `dev` means a self-built state without a released version.

The **System** page also shows the Java version, the database in use and the active profiles. If it says that the backend cannot be reached, KMM is no longer running; see [Troubleshooting](troubleshooting.md).
