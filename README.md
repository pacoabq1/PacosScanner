# Paco's Scanner

Open-source Android document scanner created by Paco in collaboration with OpenAI's ChatGPT. MIT licensed.

## v0.3 source prototype — not compiled or device-tested

- Capture a photo or import multiple pictures
- **Experimental offline automatic corner suggestion** for bright paper on contrasting backgrounds
- Manual four-corner adjustment and perspective correction
- Multi-page PDF creation, private folder paths, Android share sheet

To try: Open in Android Studio, sync Gradle, run on device, add a photo, tap **Straighten last page**, inspect the proposed corners and drag them if needed, then tap **Straighten & Save**.

Automatic edge detection is a lightweight brightness-gradient heuristic, not a production-grade detector. It can fail on low-contrast backgrounds, shadows, clutter and dark paper. In that case manual corners are shown. The prototype edits the most recent page only; EXIF orientation and large-image memory behavior require testing. There is no document browser, OCR or integrated fax service yet. Gradle wrapper is not included.

Documents remain in private app storage until shared. Export important documents before uninstalling. Do not commit scans, keys or credentials.
