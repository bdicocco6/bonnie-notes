# Bonnie Notes setup

This checklist takes the source project to an installed Samsung app. The first version needs one private web service because API keys must never be embedded in an APK.

## 1. Accounts and credentials

You need:

- An OpenAI API account with API billing enabled. ChatGPT Pro and API billing are separate.
- A Google Cloud project with the Google Drive API enabled.
- An OAuth 2.0 Web application client in that Google Cloud project.
- A small HTTPS server. Render, Railway, Fly.io, Google Cloud Run, or a secured ZimaBoard exposed through a trusted HTTPS tunnel can run the Node service.

## 2. Configure Google Drive

1. In Google Cloud Console, create or select a project.
2. Enable Google Drive API.
3. Configure the OAuth consent screen for personal/internal use as appropriate.
4. Create an OAuth client of type **Web application**.
5. Add this authorized redirect URI, replacing the hostname with your server:
   `https://notes.example.com/v1/google/callback`
6. Copy the client ID and client secret into the server environment variables.

The app requests only `drive.file`. This lets it manage files it creates, rather than granting broad access to all existing Drive files.

## 3. Deploy the backend

### Render Free (recommended when no hosting charges are allowed)

1. Put this repository in a private GitHub repository.
2. In Render, create a **Blueprint** from the repository and keep the service on
   the **Free** instance type. Do not add a payment method.
3. Add the secret environment variables requested by `render.yaml`.
4. Set `PUBLIC_BASE_URL` to the assigned HTTPS
   address and set `GOOGLE_REDIRECT_URI` to
   `https://your-render-domain/v1/google/callback`.
5. Redeploy and verify that `https://your-render-domain/health` returns
   `{"ok":true}`.
6. Connect Google Drive from the Android app. The callback page will show a
   one-time refresh token. Copy it directly into Render as
   `GOOGLE_REFRESH_TOKEN`, never into chat, and redeploy once more.

The root `Dockerfile` and `render.yaml` are ready for Render. Keep all API
keys and OAuth credentials in Render variables, never in GitHub or the APK.

### Other hosts

From `server/`:

```bash
npm install
cp .env.example .env
```

Set all values in `.env`. Generate `APP_UPLOAD_TOKEN` with a password manager or at least 32 random bytes. Keep `OPENAI_API_KEY`, `GOOGLE_CLIENT_SECRET`, and the generated Google token file private.

Start the service:

```bash
set -a
source .env
set +a
npm start
```

Verify `https://your-host/health` returns `{"ok":true}`. Use HTTPS in production. Back up the `data/` directory if you want the Google connection to survive a server rebuild.

## 4. Configure and build Android

1. Install the latest stable Android Studio.
2. Open the `android/` folder.
3. Copy `local.properties.example` to `local.properties`.
4. Keep Android Studio's generated `sdk.dir` and add:

```properties
BACKEND_URL=https://your-host/
APP_UPLOAD_TOKEN=the-same-random-token-used-by-the-server
```

5. Let Gradle sync.
6. Connect the Samsung phone by USB with USB debugging enabled.
7. Select the phone and click Run.

For a shareable signed APK, use **Build > Generate Signed Bundle / APK > APK**. Protect the signing key and its password.

## 5. First-run test

1. Open Bonnie Notes and go to Settings.
2. Tap **Test connection**.
3. Tap **Connect Google Drive**, approve the requested permission, and return to the app.
4. Test again. It should show Google Drive as connected.
5. Record a 30-second conversation after everyone consents.
6. Stop the recording, open History, and wait for the green ready indicator.
7. Confirm the transcript, tasks, and the `Bonnie Notes` folder in Google Drive.

## 6. Speakerphone test

Before relying on call capture:

1. Tell the participant that the call will be recorded and get clear consent.
2. Put the call on speakerphone.
3. Start Bonnie Notes and make a short test recording.
4. Play the local result and confirm both sides are audible.

Samsung, Android, or the carrier may reserve the microphone for the Phone app. If that happens, Bonnie Notes cannot bypass it. Use a second nearby device, an approved native call-recording feature, or an app-based calling service instead.

## Production hardening before broader use

- Put the backend behind HTTPS and firewall/rate limits.
- Replace the single shared app token with per-device authentication.
- Encrypt the Google refresh token at rest with a managed secret key.
- Add server backups and a retention policy.
- Complete a privacy policy before recording employees, donors, clients, minors, or sensitive meetings.
- Review recording-consent rules for every state or country involved.
