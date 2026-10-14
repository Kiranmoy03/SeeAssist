# Architecture

SeeAssist uses an ESP32-CAM as the visual sensor and an Android phone for control, microphone access, networking and spoken feedback. The phone communicates with an experimental AI backend over the network.

```text
ESP32-CAM --Wi-Fi image--> Android app
                              |  frame selection
                              |  SpeechRecognizer / typed question
                              v
                      Gradio/Colab backend
                         | VQA: LFM2.5-VL-3B
                         | OCR: PaddleOCR
                         | Detection: YOLO26n
                              |
                              v
                     Answer -> Android TTS
                              |
                     Bluetooth/phone audio
```

## Responsibilities

**ESP32-CAM:** expose camera images to the connected phone over Wi-Fi.

**Android:** configure camera/backend addresses, capture a fresh image sequence, select a frame, accept questions and speak answers. Hands-free/background microphone handling is under development.

**AI backend:** route requests to a suitable computer-vision task and return text responses. The development endpoint uses temporary Colab/Gradio infrastructure, so its address can change between sessions.

## Current limitations

The hardware/software link depends on network access and a running backend. AI output can be inaccurate; visual responses are not reliable enough for safety-critical navigation. The complete runnable source has not yet been exported to GitHub, so this is an architecture description rather than installation documentation.
