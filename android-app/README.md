# Android app

The SeeAssist Android client is being developed in Kotlin.

The initial prototype supports camera-image retrieval from an ESP32-CAM, best-frame selection, spoken or typed questions, AI backend requests, and spoken answers.

The latest development work covers microphone status reporting, Bluetooth audio handling and a hands-free service. These changes were built in the development environment but still require testing on a physical Android device.

**Source integrated:** The Kotlin app and Gradle launcher scripts are in this directory. Run `powershell -ExecutionPolicy Bypass -File .\scripts\setup-android-deps.ps1` from `android-app/` on Windows to fetch and verify the Gradle wrapper JAR and sherpa-onnx AAR. A clean-clone build still needs testing.

Before committing the export:
1. Remove machine-specific `local.properties`, signing credentials, generated `build/` directories and any hardcoded secrets.
2. Run the dependency setup and verify Gradle sync and a real Android build.
3. Document Android permissions, setup, how to run it and which features were actually tested.
4. Run the app on a real phone and record the results.

See [current project status](../docs/PROJECT_STATUS.md).
