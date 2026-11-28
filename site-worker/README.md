# Upload service for pacosworld.com

A small Cloudflare Worker that receives PDFs from Paco's Scanner and keeps them in a private
Cloudflare R2 bucket. It runs on Cloudflare, so uploads work while the home PC is off.

## How it is protected

1. **Cloudflare Access** covers `pacosworld.com/api/scans*`. Two ways in:
   - the phone app, with an Access **service token** (Client ID + Client Secret typed into the app's
     Settings > Upload to my site; never stored in this repo or the APK);
   - Mark in a browser, with his usual Access sign-in.
2. The Worker checks the signed `Cf-Access-Jwt-Assertion` header itself (team domain + application AUD),
   and refuses everything when `TEAM_DOMAIN` or `POLICY_AUD` is empty.

## What gets created in Cloudflare

| Thing | Name |
|---|---|
| R2 bucket | `pacos-scans` |
| Worker | `pacos-scans`, route `pacosworld.com/api/scans*`, R2 binding `SCANS` |
| Access service token | `Paco's Scanner phone` |
| Access application | `Paco's Scanner uploads`, path `pacosworld.com/api/scans` only, so the existing Paco's Home Page app is unchanged. Policies: Service Auth for that token, Allow for Mark's email |

## Endpoints

- `GET /api/scans/ping` – connection test (the app's **Test** button)
- `GET /api/scans/files` – list of uploaded PDFs (for a future Documents tab)
- `GET /api/scans/files/<folder>/<name>.pdf` – open one
- `PUT /api/scans/files/<folder>/<name>.pdf` – upload; an existing name gets ` (2)` instead of being overwritten

## Live setup (Nov 28, 2026)

Created in Mark's Cloudflare account: R2 bucket `pacos-scans`, Worker `pacos-scans` on route
`pacosworld.com/api/scans*`, Access application "Paco's Scanner uploads" (path `/api/scans` only,
policies "Only Mark" and "Paco's Scanner phone"), and the service token "Paco's Scanner phone".
The Worker's settings (team domain, AUD, allowed email, token Client ID, and the token secret as an
encrypted secret) live in Cloudflare, not in this repo.

## Phone setup

In the app: Settings > Upload to my site > **Pair with my site**. The browser opens
`pacosworld.com/api/scans/pair`; sign in with your email code, tap **Open in Paco's Scanner**, then **Set up**.

## Redeploying the code by hand (optional)

```
cd site-worker
npx wrangler deploy --keep-vars
```
`--keep-vars` keeps the settings stored in Cloudflare (the values in `wrangler.jsonc` are blank on purpose).
