# Privacy and data flow

## Where data goes

1. Audio is recorded into Bonnie Notes' private Android app storage.
2. Android uploads the completed audio file over HTTPS to the configured private backend.
3. The backend sends the recording to OpenAI for speaker-labeled transcription.
4. The backend sends the transcript to OpenAI to extract tasks, owners, and deadlines.
5. The backend returns the transcript and tasks to the phone.
6. If connected, the backend uploads a JSON notes file and the original audio to the `Bonnie Notes` folder in the user's Google Drive.
7. The backend deletes its temporary local audio copy after the request finishes.

## Security defaults in this project

- The app refuses to start recording until the user confirms participant consent.
- Recording continues only through a visible Android foreground service and notification.
- API and Google OAuth secrets are never stored in the Android source or APK.
- The Android app disallows unencrypted HTTP traffic.
- The backend uses a timing-safe bearer-token comparison and standard security headers.
- Google access uses the narrower `drive.file` scope.
- Android backups are disabled so recordings are not silently copied into device backup.

## Retention

The first version keeps the local audio, transcript, and tasks until the app is uninstalled or a future delete control is used. Google Drive files remain until deleted from Drive. Set an organizational retention policy before using the app for sensitive business, personnel, donor, client, health, or child-related discussions.

## Consent

The software does not determine whether recording is lawful. The person starting a recording is responsible for obtaining every required participant's consent and following applicable organizational policies and laws.

