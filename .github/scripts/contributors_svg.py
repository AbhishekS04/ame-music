"""Draw the README contributor grid from GitHub's live contributor list.

The grid is a single SVG with avatars embedded as base64, because an SVG shown
through an <img> may not load external resources. Layout matches contrib.rocks
(12 columns of 64px circles, 4px apart), which is what the README used before.

Invoked by `.github/workflows/contributors-image.yml`, which runs:

    GH_TOKEN=... REPO=owner/name python contributors_svg.py out/contributors.svg

The workflow publishes the result to a dedicated `contributors` branch; the
README references it through raw.githubusercontent.com. This script never
writes to `main` and never exposes the token: the GitHub API is called with it,
while avatars are fetched anonymously from the public CDN.
"""

import base64
import json
import os
import sys
import urllib.request
from xml.sax.saxutils import escape

COLUMNS = 12
SIZE = 64
GAP = 4
# Fetched at 2x so the circles stay sharp on high-density screens.
AVATAR_PX = SIZE * 2
# Accounts that show up in the commit history but are not people.
EXCLUDE = {"claude"}

USER_AGENT = "ame-contributors-svg/1.0"


def get(url, token=""):
    """Fetch a URL, authenticating only when a token is supplied."""
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": USER_AGENT,
            "Accept": "application/vnd.github+json",
        },
    )
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(req, timeout=30) as res:
        return res.read(), res.headers


def contributors(repo, token):
    """Return every human contributor for `owner/name`, across all pages."""
    users, page = [], 1
    while True:
        body, _ = get(
            f"https://api.github.com/repos/{repo}/contributors"
            f"?per_page=100&page={page}",
            token,
        )
        batch = json.loads(body)
        if not batch:
            return users
        for contributor in batch:
            login = contributor.get("login", "")
            if (
                contributor.get("type") == "User"
                and not login.endswith("[bot]")
                and login.lower() not in EXCLUDE
            ):
                users.append(contributor)
        page += 1


def avatar(contributor):
    """Embed one avatar as a data URI; no token is sent to the avatar CDN."""
    url = (
        contributor["avatar_url"]
        + ("&" if "?" in contributor["avatar_url"] else "?")
        + f"s={AVATAR_PX}"
    )
    body, headers = get(url)
    mime = headers.get("Content-Type", "image/png").split(";")[0]
    return f"data:{mime};base64,{base64.b64encode(body).decode()}"


def render(users):
    """Return the complete SVG document for the given contributor list."""
    rows = max(1, -(-len(users) // COLUMNS))
    width = COLUMNS * SIZE + (COLUMNS - 1) * GAP
    height = rows * SIZE + (rows - 1) * GAP
    radius = SIZE // 2
    out = [
        f'<svg xmlns="http://www.w3.org/2000/svg" '
        f'xmlns:xlink="http://www.w3.org/1999/xlink" '
        f'width="{width}" height="{height}" viewBox="0 0 {width} {height}" '
        f'role="img" aria-label="Ame contributors">'
    ]
    for i, contributor in enumerate(users):
        x = (i % COLUMNS) * (SIZE + GAP)
        y = (i // COLUMNS) * (SIZE + GAP)
        login = escape(contributor["login"])
        out.append(
            f'<svg x="{x}" y="{y}" width="{SIZE}" height="{SIZE}">'
            f"<title>{login}</title>"
            f'<defs><pattern id="a{i}" width="{SIZE}" height="{SIZE}" '
            f'patternUnits="userSpaceOnUse">'
            f'<image width="{SIZE}" height="{SIZE}" xlink:href="{avatar(contributor)}"/>'
            f"</pattern></defs>"
            f'<circle cx="{radius}" cy="{radius}" r="{radius - 0.5}" '
            f'stroke="#c0c0c0" stroke-width="1" fill="url(#a{i})"/>'
            f"</svg>"
        )
    out.append("</svg>")
    return "\n".join(out) + "\n"


def main():
    if len(sys.argv) != 2:
        print("usage: contributors_svg.py <output.svg>", file=sys.stderr)
        return 2

    repo = os.environ.get("REPO", "").strip()
    if not repo:
        print("REPO=owner/name is required", file=sys.stderr)
        return 2

    users = contributors(repo, os.environ.get("GH_TOKEN", ""))
    svg = render(users)
    with open(sys.argv[1], "w", encoding="utf-8", newline="\n") as f:
        f.write(svg)
    print(f"{len(users)} contributors, {len(svg) // 1024} KiB")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
