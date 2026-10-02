# Android App

This folder is for the native SeeAssist Android application.

Current Day 1 behavior:
- Connects to ESP32-CAM
- Discards 2 stale frames
- Captures 5 fresh frames
- Selects the best frame locally
- Accepts speech or typed questions
- Sends the selected image and question to the AI backend
- Speaks the returned answer
- Stores editable ESP32 IP and backend URL

Day 2 will add hands-free/background operation and wake-word support.
