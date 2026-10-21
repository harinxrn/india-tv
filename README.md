# India TV

A minimal Android TV app for watching live Indian TV channels, built for the remote first. Channels come from the free [iptv-org](https://github.com/iptv-org/iptv) playlists and refresh inside the app, so the lists stay current without an app update.

## Features

- **Malayalam News home page** — the app opens on a grid of Malayalam news channels (Asianet News, Manorama News, Mathrubhumi News, 24 News, Reporter, Media One, News18 Kerala, Kairali News, Janam TV and more). Each tile can carry a custom YouTube Live link: press OK to watch on YouTube inside the app, hold OK to add/edit the link or fall back to the channel's IPTV stream.
- **HD only** — every IPTV section lists 720p-and-above streams, judged by the resolution in the stream name.
- **Categories** — Malayalam, Sports, Movies, India, News, Entertainment, Music, Kids, Religious and more, plus channel search.
- **TV-native player** — fullscreen playback, channel up/down on the D-pad without leaving the video, pause on OK, and automatic skip to the next channel when a stream is dead.
- **Streams with special headers just work** — per-stream User-Agent / Referer hints from the playlist (EXTVLCOPT) are passed to the player.
- **Fast after first launch** — playlists are cached on the TV for a week; the Refresh button re-fetches them any time.
- Registers as a proper TV app (leanback launcher + banner) and runs fine alongside replacement launchers such as CleanYourTV.

Roughly 2,000 channels are merged from the four source playlists before the HD filter. Note that iptv-org streams are free public links: availability varies and some are region-locked. That is a property of the source, not the app.

## Remote controls

| Key | Action |
| --- | --- |
| D-pad | Browse categories and channels (focused card expands) |
| OK | Open channel / pause in player / confirm |
| Hold OK | Options for a Malayalam News tile (YouTube link, IPTV, edit) |
| Up / Down in player | Next / previous channel |
| Back | Exit player, back to grid |

## Build

No Gradle. The APK is assembled with the Android SDK build-tools directly (aapt2, ECJ, d8, zipalign, apksigner), the same manual pipeline used for the release builds.

Requirements:

- JDK 17 (javac not needed; ECJ does the compiling)
- Android SDK platform 34 + build-tools 34.0.0
- Python 3 with Pillow (only for regenerating the banner/icon)

```bash
python3 assets-img/make_img.py   # writes res/drawable/banner.png + mipmap icon
bash build.sh                    # produces build/IndiaTV-vX.Y.apk
```

`build.sh` expects the SDK at `~/android-sdk` and a signing keystore at `~/workspace/instacount/build-tmp/debug.keystore`; adjust the paths at the top of the script for your machine. The build script regenerates the images automatically if they are missing.

## Install

Sideload the APK over ADB:

```bash
adb connect <tv-ip>:5555
adb install IndiaTV-v1.4.apk
```

or copy it to a USB drive and open it from the TV's file app.

## How the YouTube playback works

For a news tile with a YouTube link, the app first tries to resolve the channel's current live stream (page scrape, then YouTube's player API as several client types) and play it in the native player. If that is refused, it plays YouTube's official embedded player inside the app, sending the Referer/origin the embed now requires. Only channels that also block embedding fall back to the YouTube (or SmartTube) app, and the app offers the IPTV stream in that case.

## Credits

Channel data: [iptv-org](https://iptv-org.github.io/) — a community-maintained collection of publicly available streams. This app is a player for those playlists; it hosts no streams itself.
