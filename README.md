# Paco's Scanner

**An open-source, privacy-first Android document scanner.**

Created by Paco in collaboration with OpenAI's ChatGPT. Licensed under MIT.

## Status: v0.1 source prototype — not yet built or device-tested

Implemented in starter source: camera capture, multiple image import, multi-page PDF creation, private on-device folder paths, and Android share sheet (email or installed fax app).

Not yet implemented: automatic corner detection, perspective correction, manual cropping, OCR, document browser, page reordering, dark mode, built-in fax integration, and public-folder export.

## Open in Android Studio

Open the PacosScanner folder, sync Gradle (Android SDK 35), and run on an Android device. The Gradle wrapper is not yet included; use a configured Gradle installation.

## Privacy

No ads, analytics or remote backend. PDFs are in private app storage and may be deleted when the app is uninstalled. Export important files. Sharing sends a file to an external app governed by its own privacy practices.

## Roadmap

- v0.2: manual four-corner crop and perspective correction
- v0.3: auto edge detection and enhancement
- v0.4: folder browser, document management and backups
- v0.5: on-device OCR and search
- v0.6: optional fax provider integration

Contributions and issues welcome. Never commit scanned documents, secrets or API keys.
