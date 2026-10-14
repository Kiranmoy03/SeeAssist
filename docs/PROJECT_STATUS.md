# Project status

Updated: 14 October 2026

## Tested in the initial prototype
The Day 1 milestone records the following as tested:
- ESP32-CAM camera capture and Android communication
- Discarding two stale frames, collecting five new frames and selecting a better image
- Voice/typed question input, backend routing to VQA/OCR/detection and spoken answers
- Bluetooth earbud audio path during initial tests

These are development observations, not formal performance or accessibility validation.

## Work in progress
The Android app currently under development includes code for microphone status, Bluetooth audio and a hands-free service. A recent development environment screenshot shows an Android build after editing six Kotlin files. This does **not** establish that hands-free operation or wake-word detection works reliably on a physical phone.

## Not completed / not verified
- End-to-end testing of the latest Android build on a real phone
- Reliable wake word and screen-off/background behavior
- Navigation / obstacle warning accuracy and safety
- Final portable power system and glasses mounting
- Standalone backend deployment without a Colab runtime
- Reproducible builds from this GitHub repository

## Current repository gap
The repository contains notes and milestones, but the latest Android sources, ESP32 firmware sketch, Python receiver and Colab notebook still need to be exported and added. Test those files and remove credentials before committing.

## Next checkpoint
Commit sanitized project files, provide build/setup instructions, then reproduce the test on a phone connected to the ESP32-CAM. Record pass/fail results and known limitations.
