# SeeAssist

**An ESP32-CAM and Android-based visual assistance prototype**

SeeAssist is a student project exploring how a small camera, an Android phone and AI models can work together to answer spoken questions about nearby objects, printed text and surroundings.

The goal is a hands-free, low-cost prototype. It is still in development and is **not a replacement for a mobility aid or other safety equipment**.

## How it works

```text
ESP32-CAM (OV2640)
       |
       | Wi-Fi image capture
       v
Android phone
  - clears two buffered frames
  - captures five fresh frames and selects one
  - collects a question (voice or text)
       |
       | image + question
       v
Development AI backend (Google Colab / Gradio)
  - visual question answering / scene description
  - OCR for text
  - object detection where appropriate
       |
       | response
       v
Android TextToSpeech -> phone speaker or Bluetooth earbud
```

The Colab service currently uses:
- **LFM2.5-VL-3B** for visual question answering and scene understanding
- **YOLO26n** for object detection
- **PaddleOCR PP-OCRv6 Small** for reading text

The Android application uses Kotlin and Android speech/audio APIs. The hardware camera is an AI-Thinker ESP32-CAM with an OV2640 sensor.

## Progress

**Verified in earlier prototype tests:** ESP32-CAM image capture, Android-to-camera communication, multi-frame image selection, spoken/typed questions, AI responses and spoken playback. See [prototype test notes](docs/PROTOTYPE_TESTS.md).

**In development:** hands-free mode, wake-word detection, Bluetooth microphone handling and background operation. The Android code has been edited and built in the development environment, but reliable wake-word detection and full real-phone testing are still pending.

**Not yet validated as an assistive feature:** navigation and obstacle alerts, extended wearable battery operation, and real-world reliability testing.

For a more current breakdown, see [Project status](docs/PROJECT_STATUS.md) and [Architecture](docs/ARCHITECTURE.md).

## Repository layout

```text
android-app/   Kotlin Android app source, tests and Gradle configuration
esp32-cam/     ESP32-CAM firmware notes; sketch upload pending
receiver/      Desktop receiver notes; script upload pending
colab/         Model/backend notes; notebook upload pending
docs/          Test notes, architecture and project status
```

**Android source:** The Kotlin app now lives in [`android-app/`](android-app/). Development continues in the private AI Studio-connected `seeassist-app` repository; a GitHub Actions workflow can propose Android updates here via pull requests. The ESP32 sketch, Python receiver and Colab notebook still need to be added. A clean Android build remains unverified because the Gradle wrapper scripts/JAR and the referenced sherpa-onnx AAR are missing.

## Next steps

1. Restore missing Android build dependencies and confirm a clean build from a fresh clone.
2. Add the ESP32-CAM sketch, receiver script and runnable Colab notebook.
3. Improve and test wake-word detection, hands-free audio and phone background behavior.
4. Test on the actual hardware, then document results, limitations and setup steps.
5. Add a short demonstration and photos of the assembled prototype when ready.

## Privacy and safety

- Never commit Wi-Fi credentials, API keys, access tokens, keystores or personal camera captures.
- The development backend may send images to a temporary Colab runtime; avoid capturing sensitive information.
- Answers can be inaccurate or delayed. Do not rely on this prototype for road crossing, obstacle avoidance or other safety-critical decisions.

This project is being built and tested incrementally. Unfinished parts are noted instead of being presented as completed features.
