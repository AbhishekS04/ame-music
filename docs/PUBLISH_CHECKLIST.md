# Pre-push checklist

Run these from the repository root, in order. Nothing here pushes anything.

## 1. Make sure the secret files exist only on disk

```bash
ls -la keystore.properties ame-release.jks local.properties
```

All three may exist locally. None may be tracked:

```bash
git ls-files | grep -E '(keystore\.properties|local\.properties|\.jks$)' || echo "OK: none tracked"
```

## 2. Install the hook and run the gate

```bash
bash scripts/install-git-hooks.sh
git add -A
bash scripts/check-secrets.sh
```

`SECRET CHECK PASSED` is the only acceptable result.

## 3. Inspect what is about to be published

```bash
git status --short
git diff --cached --stat | tail -20
```

There must be no `node_modules/`, `build/`, `.cxx/`, `.gradle/`, `dist/`,
`logs/`, `*.jks`, `keystore.properties`, `local.properties` or `.env` file in
the staged list. If there is, the `.gitignore` is not doing its job — stop and
fix it before committing.

## 4. Build once from a clean configuration

The release build intentionally produces an *unsigned* APK when the key is
absent; that is safe for a source checkout. Locally:

```bash
./gradlew :app:assembleProdDebug
```

For a signed release, the key must exist locally and `keystore.properties` must
point at it. Never commit either.

## 5. GitHub repository settings (do this in the browser, not in git)

- Add the Actions secrets the workflows expect:
  `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`,
  `LASTFM_API_KEY`, `LASTFM_SECRET`, `LISTEN_TOGETHER_SERVER`.
- Only add `ORACLE_DEPLOY_KEY`, `ORACLE_HOST`, `ORACLE_KNOWN_HOSTS` if you
  actually use `deploy-backend.yml`.
- Enable **Secret scanning** and **Push protection** under
  Settings → Code security.
- Do **not** enable "Allow force pushes" on `main`.

If those secrets are not added yet, either add them or disable the workflows
that need them before the first push; otherwise the first push will show a red
CI run (not a leak, just a missing secret).

## 6. Announcements

`app/build.gradle.kts` reads `ANNOUNCEMENT_URL` from `local.properties` or the
environment, falling back to:

```
https://raw.githubusercontent.com/AbhishekS04/ame-music/main/announcements.json
```

After the repository is renamed or moved, override it in `local.properties`
(untracked) or as a CI environment variable. To publish a notice, edit
`announcements.json` in the repository and raise its `id`. Each device shows a
notice once and stores only that id.

## 7. Licence and attribution — do not remove

This project is a **GPLv3 derivative** of BitChord by Kushagra Singh. The
GPL requires preserving the original copyright notices and attribution. Do not
strip the `Credits & Upstream` section, the `Upstream` section in
`MAINTAINERS.md`, the licence notices, or the GPL text. Removing them is a
copyright violation and can get the repository taken down; keeping them does
not weaken the app or its branding.

## 8. Last look

```bash
bash scripts/check-secrets.sh
```

Then commit and push. You are the only one who pushes.
