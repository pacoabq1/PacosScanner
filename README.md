# Paco's Scanner

**Scan it. Straighten it. Save it. Send it.**

Open-source Android document scanner created by Paco in collaboration with OpenAI's ChatGPT. MIT licensed.

## Status: v0.4 source prototype — not compiled or device-tested

### Implemented in source
- **New visual home screen:** navy and turquoise palette, prominent Scan Document action, saved folders, recent PDFs, and light/dark/follow-system appearance preferences
- Camera capture and multi-image import
- Experimental offline document corner suggestion, manual adjustment, perspective correction
- Multi-page PDF creation and sharing through the Android share sheet
- On-device private document storage

### Planned / not yet working
The home screen is the **first implementation**, not a pixel-perfect match to the concept artwork. Its gallery shortcut currently opens the scanner workflow; saved-file rows are previews, not an interactive document browser. Automatic detection is a basic brightness heuristic and may fail on shadows or low contrast. Full camera overlay, thumbnails, folder management, document search, page reorder, OCR, integrated fax, animations, and polished icons are still planned.

### Build and test
Open this repository in Android Studio, sync Gradle, run on an Android phone. Gradle wrapper is not yet included. No compiled APK has been verified.

### Privacy
No ads or remote document processing. PDFs are saved in private app storage, which is deleted when the app is uninstalled; export important documents. Sharing with external apps is governed by those apps' policies.

### Design integrity
Any promotional images are **concept mockups**, not screenshots of the current app. Features in mockups may be aspirational until implemented and verified.

Please don't commit private documents, API keys, or credentials.
