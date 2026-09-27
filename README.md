# Seslendirmen

Seslendirmen is an offline Android document reader that extracts text from PDF and EPUB files and
reads it aloud on the device. Text-to-speech uses Supertonic 3 through sherpa-onnx. Scanned PDF
pages can be processed with the bundled Google ML Kit text-recognition component.

The app does not request Internet access. Documents, recognized text, and generated speech remain
on the device.

## Privacy

The app's data practices are described in the [Privacy Policy](PRIVACY_POLICY.md). The same policy
is bundled with the application and can be opened from **Ayarlar > Yasal bilgiler > Gizlilik**.
Before a Google Play release, publish this file at a stable, publicly accessible web URL and enter
that URL in Play Console.

## Licensing

The source code written for this repository is licensed under the Apache License 2.0; see
[`LICENSE`](LICENSE).

That license does **not** replace the licenses or terms of third-party components:

- The Supertonic 3 model weights, configuration, index, and voice-style data under
  `app/src/main/assets/tts/supertonic3/` are licensed separately under the BigScience Open RAIL-M
  License. This license contains use-based restrictions and is not equivalent to Apache-2.0 or MIT.
  See the model's [`LICENSE`](app/src/main/assets/tts/supertonic3/LICENSE) and
  [`MODEL_CARD.md`](app/src/main/assets/tts/supertonic3/MODEL_CARD.md).
- sherpa-onnx is licensed under Apache-2.0.
- ONNX Runtime is licensed under the MIT License.
- Google ML Kit is subject to the Google APIs and ML Kit Terms of Service.
- Other Android and JVM dependencies retain their respective licenses and notices.

The notices shipped with the Android application are listed in
[`THIRD_PARTY_NOTICES.txt`](app/src/main/assets/legal/THIRD_PARTY_NOTICES.txt). They can also be
opened from **Ayarlar > Yasal bilgiler** in the app.

By using or redistributing the bundled Supertonic 3 model, you must comply with its Open RAIL-M
license, including its use restrictions and redistribution conditions. Do not describe the bundled
model itself as Apache-2.0 licensed.

## Model provenance

The bundled INT8 model files come from the sherpa-onnx distribution
`sherpa-onnx-supertonic-3-tts-int8-2026-05-11`. The upstream model is Supertonic 3 by Supertone.
Checksums and modification information are recorded in the model card next to the weights.
