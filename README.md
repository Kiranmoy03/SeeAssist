# SeeAssist

**Visual Question Answering Based Assistive System for Visually Impaired People**

SeeAssist is an assistive smart-glasses prototype that uses an ESP32-CAM, an Android phone, computer vision, OCR, visual question answering, and voice interaction to help a visually impaired user understand nearby objects, text, and surroundings.

## Current Prototype

Day 1 is complete.

The working Ask Mode pipeline is:

```
ESP32-CAM
   ↓
Android phone
   ↓
Discard 2 stale frames
   ↓
Capture 5 fresh frames
   ↓
Select best frame
   ↓
Route question to VQA / OCR / Object Detection
   ↓
Return and speak answer
```

### Current AI stack

- VQA / scene understanding: LiquidAI LFM2.5-VL-3B
- Object detection: YOLO26n
- OCR: PaddleOCR PP-OCRv6 Small
- Android speech input: Android SpeechRecognizer
- Android speech output: TextToSpeech

## Planned Next Steps

- Day 2: Hands-Free Mode, wake word, and screen-off/background operation
- Day 3: Basic assistive navigation and obstacle warnings
- Later: VizWiz adaptation/fine-tuning and removal of Colab dependency
- Final: wearable battery-powered assembly

## Repository Structure

```
SeeAssist/
├── android-app/
├── esp32-cam/
├── receiver/
├── colab/
├── docs/
├── .gitignore
└── README.md
```

## Security

Do not commit Wi-Fi passwords, API keys, keystores, private credentials, temporary secrets, large model weights, or datasets.
