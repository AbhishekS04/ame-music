# Deploying the Listen Together server

The `backend/` directory is the optional Go WebSocket server behind **Listen
Together** (synchronised listening parties). It is not used for playback,
downloads, lyrics, the web player or app updates. The app works without it; only
party mode needs a server.

The server keeps each party in memory and anchors playback to its own clock. Two
consequences matter for hosting:

- **Run exactly one instance.** A second instance would hold a second, disjoint
  set of parties.
- **A restart ends every active party.** The state is not persisted.

## Render (free)

Render's free web service is the simplest permanent-free option. It sleeps after
about 15 minutes without traffic, so use the keep-alive below.

### Blueprint

A [`render.yaml`](../render.yaml) is at the repository root.

1. Render Dashboard → **New → Blueprint**.
2. Connect this repository. Render reads `render.yaml`, builds `backend/`, runs
   `./server`, and checks `/healthz`.
3. Set the region to the one closest to your users if the default is not.
4. After the first deploy, copy the service URL
   (`https://<service>.onrender.com`).

### Manual service

- **New → Web Service**, connect the repository.
- **Root Directory:** `backend`
- **Runtime:** Go
- **Build Command:** `go build -o server .`
- **Start Command:** `./server`
- **Health Check Path:** `/healthz`
- **Instances:** 1
- **Plan:** Free

Environment variables:

| Variable | Value | Why |
|---|---|---|
| `JAM_TRUST_PROXY` | `true` | Render terminates TLS; this trusts `X-Forwarded-For`. |
| `JAM_ALLOWED_ORIGINS` | `https://ame-music.vercel.app` | The web player's browser origin. Native apps send no Origin. |
| `JAM_PUBLIC_ORIGIN` | `https://<service>.onrender.com` | Optional. Only needed if invite deep links report the wrong host; leave unset to auto-detect. |

The other `JAM_*` values have safe defaults; see
[`backend/README.md`](../backend/README.md#environment).

### Keep it awake

Render sleeps a free service after ~15 minutes without traffic. A free external
monitor prevents that:

- **UptimeRobot** — HTTP(s) monitor, 5-minute interval, URL
  `https://<service>.onrender.com/healthz`, expect HTTP 200.
- **cron-job.org** — same URL, every 5–12 minutes.

This keeps the process warm. It does **not** preserve parties across a deploy or
a platform restart.

Do not use a GitHub Actions cron for this: the minimum interval is 5 minutes,
runs are delayed, scheduled workflows are disabled after long repository
inactivity, and on a private repository each run consumes Actions minutes.

## Railway

Railway can run the same Go service, but check its current pricing: as of
writing, its free offering is a limited trial credit rather than a permanent
always-on free tier.

1. **New Project → Deploy from GitHub repo**, select this repository.
2. Set the service **Root Directory** to `backend`.
3. Build command: `go build -o server .`
4. Start command: `./server`
5. Add the same environment variables as Render (`JAM_TRUST_PROXY=true`,
   `JAM_ALLOWED_ORIGINS=https://ame-music.vercel.app`).
6. **Settings → Networking → Generate Domain** to get a public HTTPS URL.
7. Keep the service at one replica.

## Point the app at the server

The address is only a default for the Listen Together screen; anything typed
there wins and persists.

- **Local builds:** add to `local.properties` (gitignored):

  ```properties
  LISTEN_TOGETHER_SERVER=https://<service>.onrender.com
  ```

- **CI/release builds:** add the same value as the
  `LISTEN_TOGETHER_SERVER` GitHub Actions secret.

Then verify:

```bash
curl https://<service>.onrender.com/healthz
# {"ok":true}
```

## Invite links

The server serves `/invite/<code>` for native deep links. If the host reported
in those links is wrong, set `JAM_PUBLIC_ORIGIN` to the server's public HTTPS
origin. Leave it unset when Render or Railway correctly forwards
`X-Forwarded-Proto` and the public host.

## Related

- [Backend protocol and environment](../backend/README.md)
- [Render blueprint](../render.yaml)
- [Security policy](../SECURITY.md)
