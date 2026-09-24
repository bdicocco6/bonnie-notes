import fs from "node:fs/promises";
import path from "node:path";
import { Readable } from "node:stream";
import { google } from "googleapis";

export class GoogleDriveArchive {
  constructor(config) {
    this.config = config;
    this.sessionToken = null;
    this.oauth = config.GOOGLE_CLIENT_ID && config.GOOGLE_CLIENT_SECRET && config.GOOGLE_REDIRECT_URI
      ? new google.auth.OAuth2(config.GOOGLE_CLIENT_ID, config.GOOGLE_CLIENT_SECRET, config.GOOGLE_REDIRECT_URI)
      : null;
  }

  isConfigured() {
    return Boolean(this.oauth);
  }

  async isConnected() {
    try {
      const token = await this.readToken();
      return Boolean(token?.refresh_token || token?.access_token);
    } catch {
      return false;
    }
  }

  getAuthorizationUrl(state) {
    if (!this.oauth) throw new Error("Google Drive is not configured");
    return this.oauth.generateAuthUrl({
      access_type: "offline",
      prompt: "consent",
      state,
      scope: ["https://www.googleapis.com/auth/drive.file"]
    });
  }

  async acceptCode(code) {
    if (!this.oauth) throw new Error("Google Drive is not configured");
    const { tokens } = await this.oauth.getToken(code);
    this.sessionToken = tokens;
    await fs.mkdir(path.dirname(this.config.TOKEN_STORE_PATH), { recursive: true });
    await fs.writeFile(this.config.TOKEN_STORE_PATH, JSON.stringify(tokens), { mode: 0o600 });
    return tokens;
  }

  async readToken() {
    if (this.config.GOOGLE_REFRESH_TOKEN) {
      return { refresh_token: this.config.GOOGLE_REFRESH_TOKEN };
    }
    if (this.sessionToken) return this.sessionToken;
    return JSON.parse(await fs.readFile(this.config.TOKEN_STORE_PATH, "utf8"));
  }

  async client() {
    if (!this.oauth) throw new Error("Google Drive is not configured");
    this.oauth.setCredentials(await this.readToken());
    return google.drive({ version: "v3", auth: this.oauth });
  }

  async ensureFolder(drive) {
    const escaped = this.config.GOOGLE_DRIVE_FOLDER_NAME.replaceAll("'", "\\'");
    const existing = await drive.files.list({
      q: `name='${escaped}' and mimeType='application/vnd.google-apps.folder' and trashed=false`,
      fields: "files(id,name)",
      spaces: "drive"
    });
    if (existing.data.files?.[0]?.id) return existing.data.files[0].id;
    const created = await drive.files.create({
      requestBody: { name: this.config.GOOGLE_DRIVE_FOLDER_NAME, mimeType: "application/vnd.google-apps.folder" },
      fields: "id"
    });
    return created.data.id;
  }

  async uploadMeeting({ title, recordedAt, audioPath, audioMime, payload }) {
    if (!(await this.isConnected())) return { uploaded: false };
    const drive = await this.client();
    const folderId = await this.ensureFolder(drive);
    const base = safeName(`${recordedAt.slice(0, 10)} ${title}`);
    const noteBytes = Buffer.from(JSON.stringify(payload, null, 2));
    const note = await drive.files.create({
      requestBody: { name: `${base}.json`, parents: [folderId], mimeType: "application/json" },
      media: { mimeType: "application/json", body: Readable.from(noteBytes) },
      fields: "id,webViewLink"
    });
    const audio = await drive.files.create({
      requestBody: { name: `${base}.m4a`, parents: [folderId] },
      media: { mimeType: audioMime, body: (await import("node:fs")).createReadStream(audioPath) },
      fields: "id,webViewLink"
    });
    return { uploaded: true, note: note.data, audio: audio.data };
  }
}

export function safeName(value) {
  return value.replace(/[\\/:*?"<>|]/g, "-").replace(/\s+/g, " ").trim().slice(0, 120);
}
