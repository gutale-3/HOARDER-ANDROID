# Enabling real offline neural voices (sherpa-onnx / Piper)

The app ships a full multi-voice Piper integration (download catalog, on-device
synthesis via `SherpaOnnxTtsEngine`, and the `com.k2fsa.sherpa.onnx` Kotlin
wrapper in `app/src/main/java/com/k2fsa/sherpa/onnx/Tts.kt`). The only piece that
cannot be committed to source control is the **native library**
`libsherpa-onnx-jni.so` (tens of MB per ABI).

Until you add it, the app still builds and runs — it simply **falls back to the
system TextToSpeech engine** whenever a Piper voice would otherwise be used
(`SherpaOnnxTtsEngine.isNativeAvailable()` returns false).

## To turn on neural Piper voices

1. Download the prebuilt sherpa-onnx **Android AAR** from the releases page:
   https://github.com/k2-fsa/sherpa-onnx/releases
   (asset name looks like `sherpa-onnx-<version>.aar`).
2. Copy that `.aar` file into this folder (`app/libs/`).
3. Rebuild. Gradle picks it up automatically via the `fileTree("libs")`
   dependency already declared in `app/build.gradle.kts`, and the native library
   is bundled into the APK.

That's it — the voice download catalog in **AI/Voice settings** will then
synthesize with real Piper models instead of falling back.

The Kotlin API in `Tts.kt` is vendored verbatim from the sherpa-onnx project so
it stays binary-compatible with the AAR you drop in here.
