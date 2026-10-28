# Paco's Scanner

Open-source Android document scanner created by Paco in collaboration with OpenAI's ChatGPT.

## v0.2 source prototype (not yet compiled or device-tested)

- Capture photos with an installed camera app or import multiple images
- **New:** Drag four corners and apply perspective correction to the last page using Android's `Matrix.setPolyToPoly`
- Combine pages into PDF, save in private app folders and share through Android

### Try it
Open in Android Studio, sync Gradle, run on your Android device. Add an image, tap **Straighten last page**, drag the cyan corner handles to the document edges, tap **Straighten & Save**, then **Save pages as PDF**.

### Limitations
The correction is manual, not automatic yet. This prototype only edits the most recently added page; camera orientation/EXIF, large PDF memory usage, and rotated imports still need testing and refinement. No OCR, built-in fax service, or document browser yet. Gradle wrapper is not included.

### Privacy
No remote server or ads. Documents are stored privately on device. Export important documents because uninstalling the app removes private storage. External sharing apps have their own privacy policies.

MIT licensed. Contributions welcome; do not commit personal scans, credentials or API keys.
