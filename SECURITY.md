# Security policy

This repository is public source. **A public repository is not a private
folder.** Anything committed is copied into GitHub's storage, into every clone,
into forks, and into GitHub's caches. Deleting the file in a later commit does
not remove it from history, and a force-push does not reliably remove it from
GitHub either.

That is the whole reason secrets are kept out of the repository *before* the
first push: once one is pushed, the only real fix is to **rotate it**.

## What must never be committed

| File / pattern | Why it is dangerous |
|---|---|
| `ame-release.jks`, `*.jks`, `*.keystore` | The signing key. Anyone with it can build an APK that Android accepts as an update to the real app. |
| `keystore.properties` | Contains `storePassword` and `keyPassword` for that key. |
| `local.properties` | Machine-specific Android SDK path; also a common place for accidental local credentials. |
| `.env`, `.env.*` | Environment secrets. Only `.env.example` (empty values) may be committed. |
| `google-services.json`, `GoogleService-Info.plist` | Firebase/Google API configuration. |
| `*.p12`, `*.pfx`, `*.pem`, `*.key`, `*.p8`, `*.mobileprovision` | Certificates and private keys. |
| `*.apk`, `*.aab` | Build output; may embed signing material or stale secrets. |
| `logs/`, `*.log` | Can contain OAuth tokens, cookies and account identifiers. |
| `node_modules/`, `build/`, `.cxx/`, `.gradle/`, `dist/` | Generated/vendor trees. Never source, always huge, and easy to hide a secret inside. |
| `.commandcode/`, `.claude/`, `.cursor/`, `AGENTS.md` | Local AI/editor configuration that may contain personal notes. |

The tracked template `keystore.properties.example` has **empty** values and is
safe to commit. It documents the keys; it never holds the values.

## How this repository enforces it

1. **`.gitignore`** excludes all of the above.
2. **`.githooks/pre-commit`** refuses to commit a secret-bearing filename or a
   high-signal credential pattern. Install once with:
   ```bash
   bash scripts/install-git-hooks.sh
   ```
3. **`scripts/check-secrets.sh`** checks the files git is actually going to
   publish. Run it after `git add -A`, before committing.
4. **`.github/workflows/secret-scan.yml`** runs on every push and pull request:
   it fails if a secret file is tracked or if gitleaks finds a credential.
5. **`.gitleaks.toml`** extends gitleaks' default rules. Its allowlist covers
   only two deliberately public third-party constants (YouTube's public
   InnerTube key and Musixmatch's public web signing constant) and generated
   directories.

## Why the announcement feature sends no data

The in-app announcement feature performs one plain `GET` to a static JSON file
and shows a notice if its `id` is newer than the last one stored on the device.
It sends **no** cookie, account, install id, device id, query string, or
custom identifying header. The shared HTTP client has no cookie jar, and its
only credential interceptor is scoped to a user-configured WebDAV host.

The host still sees the source IP in its normal access log — that is true of any
web request. There is no analytics endpoint and no database of users. The only
state is `last_seen_notice_id` in the app's local preferences.

## If a secret is ever exposed

1. Treat it as compromised immediately.
2. **Rotate it**: generate a new signing key, API key, password or token.
3. Remove the file from the working tree and from git tracking.
4. If it was pushed, rewrite history only if you accept the risk; rotation is
   still mandatory because clones and caches may already hold it.
5. Check the provider's dashboard for unexpected use.

## Reporting a vulnerability

Please do not open a public issue for a security problem. Contact the
maintainer through the address on the GitHub profile linked in
[MAINTAINERS.md](MAINTAINERS.md).
