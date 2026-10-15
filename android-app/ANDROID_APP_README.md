# SeeAssist Android App

An Android application for the **SeeAssist** smart-glasses prototype. The goal is to help a user ask questions about what an ESP32-CAM sees and hear the answers through the phone or a Bluetooth earbud.

This repository contains the **Kotlin Android client**. Camera firmware, the Colab AI backend and the wider project notes are tracked separately in [SeeAssist](https://github.com/Kiranmoy03/SeeAssist).

## How it works

1. Connect the Android phone to an ESP32-CAM over Wi-Fi.
2. Capture images from the camera. The app clears two stale frames, then evaluates five fresh frames and selects one.
3. Ask a question by voice or typing.
4. Send the selected image and question to the development AI backend.
5. Display and speak the returned answer.

The app also contains work-in-progress **hands-free mode** and local **"Hey SeeAssist"** wake-word detection using sherpa-onnx.

## Tech stack

- **Android:** Kotlin, Jetpack Compose, Material 3
- **Camera:** AI-Thinker ESP32-CAM with OV2640, HTTP image capture
- **Voice:** Android SpeechRecognizer, TextToSpeech, Bluetooth audio routing
- **Wake word:** sherpa-onnx Zipformer model (local inference)
- **Development AI backend:** Google Colab/Gradio; separate VQA, OCR and object-detection components

## Project status

| Part | Status |
| --- | --- |
| Android UI and capture/voice workflow | Implemented; earlier prototype flow tested |
| ESP32-CAM and AI backend communication | Tested in the initial prototype |
| Best-frame selection | Implemented and covered by local test cases |
| Hands-free mode and wake word | Code added; real-device behavior still needs verification |
| Navigation and obstacle warnings | Experimental, **not validated** for safe use |
| Standalone fresh-clone Android build | **Not verified**; see build notes below |

A successful build or UI preview in a development environment is not proof of end-to-end operation on a real phone.

## Running the app (current limitations)

This is a **development snapshot**, not a ready-to-install release.

- Android Studio and an Android SDK compatible with the project are required.
- The Gradle wrapper scripts (`gradlew`, `gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar`) have not yet been committed.
- `app/build.gradle.kts` expects `app/libs/sherpa-onnx-1.13.8.aar`, which has not yet been committed. Get an appropriate compatible artifact from the official sherpa-onnx distribution and verify its origin and licence before adding it.
- The current debug signing configuration points at a local `debug.keystore` file; confirm a working local debug-signing setup before building.
- An ESP32-CAM and a reachable development backend are required for the full workflow.

Once those dependencies are resolved, import this project into Android Studio, sync Gradle, and run on a physical Android phone. Record the actual build and test results before declaring the app reproducible.

### App configuration

The settings screen allows the ESP32-CAM IP address and AI backend URL to be changed. The defaults currently refer to a developer's local camera address and an **ephemeral** Gradio URL; replace them with your own endpoints during testing. Do not use someone else's captured images or a public backend without permission.

A Gemini template `.env.example` and some Firebase/AI Studio scaffold dependencies remain in the project. The documented camera-to-Colab workflow does not require a Gemini API key in the Android APK. These template pieces are pending a separate dependency review.

## Repository layout

```text
app/src/main/java/com/example/
  audio/         Speech recognition, TTS and Bluetooth audio
  camera/        ESP32 capture and frame selection
  network/       AI backend client
  processor/     Question-processing workflow
  service/       Hands-free foreground service
  settings/      Configurable endpoints
  ui/            Compose screens
  viewmodel/     Application state
  wakeword/      Local keyword spotting
app/src/test/     Local Android test cases
app/src/main/assets/  Wake-word model assets
gradle/          Build configuration
```

## Safety and privacy

SeeAssist is an **experimental assistive prototype**. AI answers can be wrong, late or incomplete. Do not depend on it for road crossing, obstacle avoidance, or other safety-critical decisions.

Images are sent to a development backend for analysis, so avoid filming personal or sensitive information. Don't commit Wi-Fi passwords, access tokens, signing keys, local camera captures or private backend credentials.

The included third-party wake-word model is attributed in its asset directory. Check the model and native-library licences before redistributing an APK.

## Next work

- Restore missing Android build dependencies and verify a clean-clone build
- Test microphone and Bluetooth routing on a real phone
- Verify wake-word detection and screen-off/background behavior
- Remove unused template dependencies only after a successful build and regression check
- Add real hardware photos, test results and a short demonstration when available
