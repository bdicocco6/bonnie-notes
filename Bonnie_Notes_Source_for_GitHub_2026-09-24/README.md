# Bonnie Notes

Bonnie Notes is a private Android AI notetaker designed for a Samsung phone. It records microphone audio, sends completed recordings to a private backend, creates a speaker-labeled transcript, extracts assignments and deadlines, saves a searchable copy in the app, and can archive the recording and notes in Google Drive.

## What this first version supports

- In-person conversations
- Speakerphone audio when the phone and Android permit microphone access
- Zoom, Google Meet, or Teams audio played aloud near the phone
- Explicit consent confirmation before recording
- Background recording with a persistent Android notification
- Speaker-labeled transcripts
- Tasks with owner, deadline, and source quote
- Search across meeting titles, transcripts, speakers, and tasks
- Google Drive archival through the private backend

## Important call-recording limitation

Android does not guarantee that a third-party app can capture cellular call audio. Bonnie Notes records the phone microphone. During a speakerphone call, success depends on the Samsung model, Android version, carrier, and whether the Phone app allows concurrent microphone access. Always test before relying on it. The app never bypasses Android recording restrictions.

## Project layout

- `android/`: Native Kotlin/Jetpack Compose Android application
- `server/`: Private Node.js backend for transcription, task extraction, and Google Drive upload
- `docs/SETUP.md`: Exact deployment and Samsung installation checklist
- `docs/PRIVACY.md`: Data flow, security defaults, and deletion guidance

## Fast start

1. Deploy the backend by following `docs/SETUP.md`.
2. Open `android/` in Android Studio.
3. Set `BACKEND_URL` and `APP_UPLOAD_TOKEN` in `android/local.properties`.
4. Build and install the debug APK on the Samsung phone.
5. In the app, open Settings and test the backend and Google Drive connection.

Do not put an OpenAI key in the Android application. The key belongs only on the backend.

