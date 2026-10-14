# Prototype test notes

These notes summarize the **earlier development tests** recorded for SeeAssist. They are not a formal performance study or independent validation.

## Camera and image selection

- Tested ESP32-CAM capture on the AI-Thinker board with an OV2640 sensor.
- Tested requesting a fresh camera image from Android over Wi-Fi.
- Used two discarded frames followed by five captured frames to reduce stale/poor captures.
- Used frame-quality scoring to select a candidate image for AI processing.

The five-frame approach is an engineering choice for the prototype, **not** a claim that it is always better or faster. Capture latency, blur reduction, and success rates still need to be measured.

## Question and answer flow

Earlier prototype tests recorded:
- Spoken and typed question input on Android
- Request handling through a temporary Colab/Gradio backend
- Routing questions to visual question answering, OCR or object detection
- Short answers, broader scene descriptions and Android text-to-speech output
- An initial Bluetooth earbud voice path test
- Editable camera IP address and backend URL

The backend used **LFM2.5-VL-3B** for visual understanding, **YOLO26n** for object detection and **PaddleOCR PP-OCRv6 Small** for OCR.

## What needs another test

| Area | Next verification |
| --- | --- |
| Android build | Build the exported source on a clean machine after restoring missing dependencies |
| ESP32 capture | Measure successful captures and average response time across repeated trials |
| OCR | Test printed text under different lighting and focus conditions |
| Object detection | Check false detections and counting accuracy using recorded test examples |
| Hands-free audio | Test wake phrase, Bluetooth mic fallback, interruptions and screen-off behavior on a real phone |
| End-to-end | Record request-to-answer time and error handling with the real camera and backend |

## Known limitations

- The temporary Gradio endpoint changes when the Colab runtime is restarted.
- Both image quality and AI accuracy depend on lighting, connectivity and available compute.
- The current prototype has **not** been validated for navigation, obstacle avoidance or other safety-critical decisions.

As testing continues, record the Android/device version, hardware configuration, test conditions, observed behavior, failures and any measurements. Screenshots or videos should only be added with consent and after checking for private information.
