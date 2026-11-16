# Free My TV Enhanced

A fork of [Free My TV](https://github.com/simbasjungles/free-my-tv) by simbasjungles, with extra home screen features
for Google TV and Android TV, by iZsk. Everything the original does still works the same way. The package stays
`io.freemytv`, so it installs over a Free My TV build signed with the same key. Only the Google TV / Android TV app
has the additions (the LG and Samsung web app is the original's).

**Version:** Enhanced has its own numbers, starting at **1.0.0**. Home Settings shows which Free My TV release it is
based on, for example *Free My TV Enhanced 1.0.0 · based on Free My TV 1.5*.

## What's new compared with Free My TV

| Area | Free My TV | Enhanced adds |
|---|---|---|
| **Picks (cards at the top)** | Trending titles on your services | Up to 7 cards per tab; a *Highlight* focus ring (Picks → Card focus); badges on each card: runtime or seasons, TMDB rating, and the year (a show's span, such as *2022–26*) |
| **Suggestions** | Trending only | *Your taste*: TMDB recommendations from the genres and titles you like. Hold OK on a card for *Love it* / *Not for me* |
| **Open picks with** | The service's app | Any app that opens `nuvio://tmdb/...` links (Nuvio and its forks). With two or more, OK asks which one |
| **Rows under the shelf** | – | *Continue Watching* as its own row; up to three *genre rows* (popular titles of a genre on your services) |
| **Title line** | – | The time and the weather next to the picks' title |
| **All Apps** | Open, Move, shelf | Hold OK for a menu: Move, Add to / Remove from shelf, **Add to folder**, **Select several**, App info |
| **Folders** | – | Apple TV style folders with a 2×2 preview; rename, delete, put a folder on the shelf, move apps between folders |
| **Select several** | – | Tick apps with OK, then put them all in a folder or on the shelf at once (inside a folder: remove or move them) |
| **TV Status** | – | A glass panel over the home screen *or over any app*: memory, storage, CPU, uptime, network; **Free memory**, **Clear caches**, **Restart home screen**, **Reboot TV**, and the **Performance mode** switch |
| **Opening TV Status** | – | Drawer → Shortcuts, a top button, a remote button (Remote Buttons → *TV Status*), or **two quick presses** of a remote button you choose (Home Settings → *TV Status button*) |
| **Drawer shortcuts** | Fixed list | Home Settings → *Shortcuts*: show or hide each one and change their order |
| **Top buttons** | Settings, Recent Apps… | Also All Apps, TV Settings and TV Status |
| **Performance mode** | – | Flat panels instead of glass and no blurred shadows, for slower TVs (TV Status, or Picks → last row) |
| **Several TVs** | – | Copy one TV's on-screen settings, folders and remote buttons to others: `./sync-settings.sh FROM_IP TO_IP` |
| **Measuring** | – | `./perf-check.sh <IP>`: the home screen's frame times and memory, for before/after comparisons |
| **Updates** | Checks Free My TV's releases | Checks Enhanced's releases, and says when a newer Free My TV is out |

Every change to an original file carries a `[custom]` comment, so merging the original's updates stays easy.

## Setting it up

You need a computer (macOS, Linux, or Windows with Git Bash), the TV's IP address, and about 20 minutes.

1. **Tools:** a JDK 17+, the Android SDK command-line tools (build-tools 34, platform android-34) and adb.
   On macOS: `brew install --cask temurin android-commandlinetools android-platform-tools`, then
   `sdkmanager "build-tools;34.0.0" "platforms;android-34"`.
2. **Get the code:** `git clone https://github.com/sk-izsk/free-my-tv-enhanced.git && cd free-my-tv-enhanced`
3. **Settings:** run `./build.sh` once. It copies [`config.example.properties`](config.example.properties) to
   `config.properties`. Fill in your city (`latitude`, `longitude`, `city`), `tmdb_region` (your country) and,
   optionally, the keys below. `config.properties` is yours: never commit it.
4. **Build:** `./build.sh` again. It must end with `Built FreeMyTV.apk`. The first build makes `freemytv.keystore`;
   keep it, because every update must be signed with the same key.
5. **On the TV:** turn on Developer options (Settings → System → About → press *Android TV OS build* 7 times), then
   *USB debugging*.
6. **Install:** `adb connect <TV IP>:5555` (accept *Allow USB debugging?* on the TV and tick *Always allow*), then
   `adb install -r FreeMyTV.apk`. If it hangs, Google Play Protect is asking on the TV: *More details* → *Install
   anyway*.
7. **Switch:** on the TV, open *Free My TV Enhanced* → *Home Screen* → *Free My TV Enhanced*. *Original* brings
   the TV's own home screen back at any time.

The original's [README](README.md) has more on each TV brand, the screensaver and the optional servers (Plex,
Immich, Home Assistant, Trakt).

## The keys, and what works without them

Both keys are free and optional. They go in `config.properties`, or are typed on the TV (Home Settings → *TMDB key*
or *Watchmode key*, with a QR code for your phone).

| Without… | What you lose | What still works |
|---|---|---|
| **TMDB key** (themoviedb.org → Settings → API) | The picks at the top, their badges, *Your taste*, the genre rows, *Open picks with*, and the picks' pictures on the screensaver | Everything else: the shelf, All Apps, folders, Select several, TV Status, Performance mode, the Continue Watching row (it comes from the TV), the weather, the clock, remote buttons, the screensaver with your own photos, settings sync |
| **Watchmode key** (watchmode.com) | A pick opens its streaming app's home instead of the title's own page | Everything else |

## Versions and keeping up with Free My TV

- Enhanced's version (`versionName` in `app/AndroidManifest.xml`) is its own: 1.0.0, then 1.0.1 for fixes, 1.1.0
  for new features. `versionCode` starts at 100 and goes up by one with every release. `Updates.BASED_ON` names the
  Free My TV release last merged.
- *Home Settings → Updates* checks this repository's releases (tag `v1.0.0` and so on). It also says *Free My TV 1.6
  is out* when the original has a newer release than `BASED_ON`.
- To bring in the original's changes:
  ```bash
  git remote add upstream https://github.com/simbasjungles/free-my-tv.git   # once
  git fetch upstream
  git merge upstream/main          # merge, never reset: keeps the [custom] changes
  ./build.sh                       # then raise the version and BASED_ON, and install
  ```
  Conflicts only happen where a `[custom]` change and an original change touch the same lines; keep both.

## Credits

- [Free My TV](https://github.com/simbasjungles/free-my-tv) by simbasjungles: everything this builds on.
- [Weatherglass TV](https://github.com/faizaldjoo/weatherglass-tv) by faizaldjoo: the TV Status idea (no code from
  it).
- Data from [TMDB](https://www.themoviedb.org) (this product uses the TMDB API but is not endorsed or certified by
  TMDB).
