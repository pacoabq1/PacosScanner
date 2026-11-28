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

## Deploy by hand (optional)

```
cd site-worker
npx wrangler r2 bucket create pacos-scans
npx wrangler deploy
```
Then set `TEAM_DOMAIN` (for example `yourteam.cloudflareaccess.com`) and `POLICY_AUD` in `wrangler.jsonc` and deploy again.
