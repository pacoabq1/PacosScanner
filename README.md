# Paco's Scanner

**Scan it. Straighten it. Save it. Send it.**

Open-source Android document scanner created by Paco in collaboration with OpenAI's ChatGPT. MIT licensed.

## Status: v1.0 — tested on a Galaxy S24

### Implemented in source
- **New visual home screen:** navy and turquoise palette, prominent Scan Document action, saved folders, recent PDFs, and light/dark/follow-system appearance preferences
- **Scan Document** opens Google's on-phone document scanner (ML Kit): automatic page edges, straightening, cleanup filters and multi-page capture. Runs on the phone through Google Play services; nothing is uploaded.
- Camera capture and multi-image import, with photos turned upright automatically (fixes sideways pages from phone cameras)
- Experimental offline document corner suggestion, manual adjustment, perspective correction
- Multi-page PDF creation, saved to Documents/PacosScanner on the phone, with Open and Share options
- Tap a recent document or folder on the home screen to open it in the phone's PDF viewer
- Name each PDF when saving (a dated name is filled in), and rename saved PDFs later from the home screen (Rename, or press and hold in a folder list)
- On-device private document storage
- **Upload to my site (testing):** send a saved PDF to private storage on pacosworld.com, automatically after each save or from the document list. Set up in Settings > Upload to my site with a Cloudflare Access service token. The site side lives in `site-worker/`.

### Planned / not yet working
The home screen is the **first implementation**, not a pixel-perfect match to the concept artwork. Its gallery shortcut currently opens the scanner workflow; saved-file rows are previews, not an interactive document browser. Automatic detection is a basic brightness heuristic and may fail on shadows or low contrast. Full camera overlay, thumbnails, folder management, document search, page reorder, OCR, integrated fax, animations, and polished icons are still planned.

### Build and test
Every push to GitHub builds the app automatically (see the Actions tab). Official versions from main are on the **Releases** page (for example v1.0.0), and the newest work-in-progress build is the **Test build** release:

1. On your phone, open this repository's **Releases** page and tap **PacosScanner-test.apk**.
2. Open the downloaded file. The first time, Android asks you to allow installs from your browser; allow it, then tap Install.
3. Later builds install over the old one and keep your saved documents, because every build is signed with the same debug-only key (`app/ci-debug.keystore`, not a secret).

You can still open the project in Android Studio and run it on a phone. The Gradle wrapper is now included.

### Privacy
No ads or remote document processing. Nothing leaves the phone unless you set up Upload to my site, which sends PDFs only to your own site. Each PDF is saved on the phone in Documents/PacosScanner/<folder> (visible in My Files, kept if the app is uninstalled), plus a copy inside the app for the home screen list. Sharing with external apps is governed by those apps' policies.

### Design integrity
Any promotional images are **concept mockups**, not screenshots of the current app. Features in mockups may be aspirational until implemented and verified.

Please don't commit private documents, API keys, or credentials.
