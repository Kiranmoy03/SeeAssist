# Desktop Receiver

This folder is for the Python receiver used during development and testing.

Working development flow:
- Flush 2 buffered frames
- Capture 5 fresh ESP32-CAM images
- Score image quality
- Select the best frame
- Send the frame and question to the SeeAssist AI backend
