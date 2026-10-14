# Android app

The SeeAssist Android client is being developed in Kotlin.

The initial prototype supports camera-image retrieval from an ESP32-CAM, best-frame selection, spoken or typed questions, AI backend requests, and spoken answers.

The latest development work covers microphone status reporting, Bluetooth audio handling and a hands-free service. These changes were built in the development environment but still require testing on a physical Android device.

**Source upload pending:** The Kotlin/Gradle Android project is not yet stored in this repository. A fresh-clone build is not currently possible.

Before committing the export:
1. Remove machine-specific `local.properties`, signing credentials, generated `build/` directories and any hardcoded secrets.
2. Include Gradle wrapper, app sources, manifest and version configuration.
3. Document Android permissions, setup, how to run it and which features were actually tested.
4. Run the app on a real phone and record the results.

See [current project status](../docs/PROJECT_STATUS.md).
