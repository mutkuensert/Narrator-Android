# Narrator (Seslendirmen)

An offline Android reader that extracts text from PDF and EPUB documents and narrates it entirely
on the device. Narrator uses the Supertonic 3 speech model through sherpa-onnx and falls back to
on-device Google ML Kit OCR when a PDF page does not contain usable embedded text.

The app does not request Internet access. Selected documents, extracted or recognized text, saved
reading positions, and generated audio remain on the device.

## Features

- Opens PDF and EPUB files through Android's system document picker; no broad storage permission is
  required.
- Extracts embedded PDF text and automatically applies OCR to scanned or image-only pages.
- Reads EPUB content in spine order and uses the book metadata title when available.
- Generates Turkish or English speech locally with the bundled Supertonic 3 INT8 model.
- Provides play, pause, previous, next, and restart controls in the app, plus playback controls in a
  media notification.
- Starts narration from a tapped paragraph and follows the active paragraph while reading.
- Saves the last reading position for each document and lets users remove saved positions from
  Settings.
- Offers a speech-quality control from 1 to 20 generation steps (the default is 6).
- Includes Turkish and English interface resources and exposes privacy and license documents inside
  the app.

## Supported devices and formats

| Item | Support |
| --- | --- |
| Android | Android 7.0 (API 24) or newer |
| Documents | PDF and EPUB |
| Narration languages | Turkish and English |
| OCR | On-device Latin-script text recognition for PDF fallback |
| Encrypted PDFs | Not currently supported |

Large documents and OCR-heavy PDFs can take longer to prepare. Speech generation speed and memory
use also depend on the device; lowering the quality step count improves generation speed at the
cost of naturalness.

## Getting started

### Prerequisites

- Android Studio with Android SDK 37 installed
- Git LFS, which is used for the bundled `.onnx` model files

The repository configures its Gradle daemon toolchain automatically and includes the Gradle wrapper.

### Build and run

```bash
git clone <repository-url>
cd Narrator-Android
git lfs pull
./gradlew :app:assembleDebug
```

Install the debug build on a connected device or emulator with:

```bash
./gradlew :app:installDebug
```

You can also import the repository root into Android Studio and run the `app` configuration. The
first build needs network access to resolve Gradle and Maven dependencies; the application itself
does not need network access at runtime.

### Tests

Run the local unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

With a device or emulator connected, run the instrumented tests:

```bash
./gradlew :app:connectedDebugAndroidTest
```

## How it works

1. `LocalDocumentRepository` receives a URI from Android's document picker and dispatches it to the
   PDF or EPUB reader.
2. PDFBox extracts PDF text. Pages without meaningful text are rendered and passed to ML Kit OCR.
   EPUB files are parsed from their package manifest and spine.
3. Extracted paragraphs are split into locale-aware speech chunks.
4. sherpa-onnx runs the bundled Supertonic 3 model on CPU. The next chunk is prefetched while the
   current chunk is played through `AudioTrack`.
5. A foreground media service keeps playback controls available, while local preferences retain
   narration settings and per-document reading positions.

The code is organized as a single Android application module with feature-oriented presentation,
domain, and data layers under
`app/src/main/java/com/mutkuensert/narrator/feature/reader/`.

## Main technologies

- Kotlin, coroutines, and `StateFlow`
- Jetpack Compose and Material 3
- Dagger Hilt
- PDFBox-Android
- Google ML Kit Text Recognition
- sherpa-onnx and ONNX Runtime
- Supertonic 3 INT8 model assets

Dependency versions are centralized in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Privacy

The app intentionally removes `INTERNET` and `ACCESS_NETWORK_STATE` from its merged manifest. It has
no account system, advertising SDK, analytics, or developer backend. Android backup is disabled for
app-managed local data.

See the bilingual [Privacy Policy](PRIVACY_POLICY.md) for details, including ML Kit's own technical
data disclosures. The same policy is bundled in the app under **Settings > Legal information >
Privacy**. Before publishing to Google Play, host that policy at a stable public URL and add the URL
in Play Console.

## Licensing

The source code written for this repository is licensed under the [Apache License 2.0](LICENSE).
Third-party software and model assets retain their own licenses and terms:

- The Supertonic 3 files under `app/src/main/assets/tts/supertonic3/` are governed by the
  **BigScience Open RAIL-M License**, which contains use-based restrictions and redistribution
  conditions. They are not Apache-2.0- or MIT-licensed. See the bundled model
  [license](app/src/main/assets/tts/supertonic3/LICENSE) and
  [model card](app/src/main/assets/tts/supertonic3/MODEL_CARD.md).
- sherpa-onnx and most Android/JVM components are distributed under Apache-2.0.
- ONNX Runtime is distributed under the MIT License.
- PDFBox-Android is distributed under Apache-2.0.
- Bouncy Castle components use the Bouncy Castle License.
- Google ML Kit is subject to the Google APIs and ML Kit terms.

The complete notices shipped with the application are in
[`THIRD_PARTY_NOTICES.txt`](app/src/main/assets/legal/THIRD_PARTY_NOTICES.txt) and are available from
**Settings > Legal information > Notices**. Anyone redistributing the application must preserve the
applicable notices and comply with the Supertonic 3 model license in particular.

## Model provenance

The bundled model files come from the sherpa-onnx package
`sherpa-onnx-supertonic-3-tts-int8-2026-05-11`, derived from Supertonic 3 by Supertone. The project
has not further modified those converted files. Their SHA-256 checksums and provenance are recorded
in the bundled [model card](app/src/main/assets/tts/supertonic3/MODEL_CARD.md).
