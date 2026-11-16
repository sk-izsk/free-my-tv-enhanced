# Free My TV Enhanced

A fork of [Free My TV](https://github.com/simbasjungles/free-my-tv) by simbasjungles, with extra home screen features
for Google TV and Android TV, by iZsk. Everything upstream does still works the same way; the package stays
`io.freemytv`, so it installs over an existing Free My TV build signed with the same key. Only the Google TV /
Android TV app has the additions (not the LG and Samsung web app).

Every change to an upstream file carries a `[custom]` comment, so upstream updates merge cleanly
(`git fetch upstream && git merge upstream/main`).

## What's added

**Picks (the cards at the top)**
- Up to 7 cards per tab, with a *Highlight* focus (Home Settings → Picks → Card focus).
- Badges on each card: runtime or seasons (top left), TMDB rating (top right), year (bottom left).
- *Suggestions: Your taste*: picks from TMDB recommendations for the titles and genres you like (Picks → Your taste);
  hold OK on a card for *Love it* / *Not for me*.
- *Open picks with*: send a pick to an app that opens `nuvio://tmdb/...` links (Nuvio and its forks); with two or
  more apps, OK asks which one.
- The time and weather in the title line (Picks → Time and weather).

**Rows under the shelf**
- *Continue Watching* as its own row (Picks → Continue Watching row).
- Up to three *genre rows* (Picks → Genre rows): TMDB's most popular titles of that genre on your services, movies on
  the Movies tab and shows on the Shows tab, refreshed every 3 hours.

**All Apps**
- Hold OK on an app: Move, Add to / Remove from the shelf, Add to folder, Select several, App info.
- *Folders*, as on Apple TV: a 2×2 preview tile; a folder can sit on the shelf; rename, delete, move apps between
  folders.
- *Select several*: tick apps with OK, then hold OK to put them all in a folder or on the shelf (inside a folder:
  remove or move them all).

**TV Status**
- A glass panel over the home screen (drawer → Shortcuts → TV Status, or a top button): memory, storage, CPU, uptime,
  network; Free memory, Clear caches, Restart home screen, Reboot TV. Inspired by the TV Status screen of
  [Weatherglass TV](https://github.com/faizaldjoo/weatherglass-tv) (MIT); no code from it.

**Other**
- Top buttons can also be All Apps, TV Settings and TV Status.
- *Performance mode* (Picks → last row): flat panels instead of glass, no blurred shadows.
- Copy one TV's on-screen settings to another over adb: `./sync-settings.sh FROM_IP TO_IP` (the `Backup` provider,
  adb's shell only).
- `./perf-check.sh <IP>`: frame times and memory of the home screen, for before/after comparisons.

## Building

As upstream (see [README.md](README.md) and [CLAUDE.md](CLAUDE.md)): `./build.sh` creates `config.properties` the
first time; put your own TMDB key there (never commit it). Optional: `open_with=` a comma-separated list of package
names for *Open picks with*. Keep your `freemytv.keystore`: updates must be signed with the same key. This fork has no
release APK of its own, so the in-app updater (which checks upstream's releases) only reports, never installs.

## Credits

- [Free My TV](https://github.com/simbasjungles/free-my-tv) by simbasjungles: everything this builds on.
- [Weatherglass TV](https://github.com/faizaldjoo/weatherglass-tv) by faizaldjoo: the TV Status idea.
- Data from [TMDB](https://www.themoviedb.org) (this product uses the TMDB API but is not endorsed or certified by
  TMDB).
