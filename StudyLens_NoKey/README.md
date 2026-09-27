# StudyLens — No-Key UI Edition

A polished Android client for turning textbook photos into topic-organized revision notes.

## UI
- Gradient hero upload card
- Subject + English/Urdu controls
- Unlimited app-level photo queue
- Page queue with removal
- Processing progress
- Separate Study and Notes tabs
- Topic cards
- Empty states and error cards
- No API key screen

## Architecture
Phone -> StudyLens backend -> AI API -> notes

The AI secret stays on the backend, not inside the APK.

## Before building
Replace `backendUrl` in:
`app/src/main/java/com/studylens/app/MainActivity.kt`

with your deployed backend `/analyze` URL.

Then open the project in Android Studio and use:
Build -> Build APK(s)

The generated APK will be under:
`app/build/outputs/apk/`

There is no fixed page-count limit in the app, but real limits still exist for phone storage,
network transfer, backend limits, model context and API usage.
