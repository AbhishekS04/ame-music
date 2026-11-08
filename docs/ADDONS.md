# Addons

Ame ships with **no addons preconfigured**. YouTube Music is the built-in
source, and JioSaavn is present but off by default. Everything else is a
choice the listener makes.

## Add one manually

1. Open **Settings**.
2. Open **Sources** (under Audio quality).
3. Tap **Add an addon**.
4. Paste the addon's URL, tap **Test**, then **Save**.

The URL may be the addon's root (`https://addon.example.com`) or its
`manifest.json`; Ame detects which kind of source it is. An addon can be
removed from its row with **Remove Source**, and the drag handle reorders
which addon is asked first.

## Links that older builds seeded automatically

These used to be added to every fresh install. They are **no longer added by
Ame**. Paste one only if you trust the operator: they are third-party servers,
not run by this project, and can change or go offline.

- `https://bitchord-monochrome-addon.onrender.com`
- `https://soundfean-musics.onrender.com/a`
- `https://stream.pencarimovie.com/66ff097863dedb7ccd8401d5ffe616f3/music`

The first is the addon that was formerly seeded first. The other two were also
defaults in earlier builds.

## What an addon is

An addon is an HTTP service that answers Ame's source protocol — a
`manifest.json` plus search/track endpoints. It is not a credential and it is
not bundled with the app; Ame only stores the URL you paste, in its encrypted
source preferences, and contacts it when resolving a track.

An addon that says `checkValidLossless: 1` is not contacted at all unless a
lossless output is connected. An addon that says `allowDownloads: 0` is skipped
for downloads. Both rules are explained in the Sources screen.
