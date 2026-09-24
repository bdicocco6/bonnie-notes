import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import crypto from "node:crypto";
import express from "express";
import helmet from "helmet";
import multer from "multer";
import { bearerAuth } from "./auth.js";

export function createApp({ config, processor, driveArchive }) {
  const app = express();
  const upload = multer({
    dest: path.join(os.tmpdir(), "bonnie-notes"),
    limits: { fileSize: config.MAX_UPLOAD_MB * 1024 * 1024, files: 1 }
  });
  const requireAuth = bearerAuth(config.APP_UPLOAD_TOKEN);
  const connectionStates = new Map();

  app.disable("x-powered-by");
  app.use(helmet());
  app.use(express.json({ limit: "64kb" }));

  app.get("/health", (_req, res) => res.json({ ok: true }));

  app.get("/v1/status", requireAuth, async (_req, res) => {
    res.json({
      ok: true,
      googleDriveConfigured: driveArchive.isConfigured(),
      googleDriveConnected: await driveArchive.isConnected()
    });
  });

  app.post("/v1/google/connect-link", requireAuth, (_req, res) => {
    if (!driveArchive.isConfigured()) return res.status(503).send("Google Drive is not configured.");
    const state = crypto.randomBytes(24).toString("base64url");
    connectionStates.set(state, Date.now() + 10 * 60 * 1000);
    res.json({ url: `${config.PUBLIC_BASE_URL}/v1/google/start?state=${encodeURIComponent(state)}` });
  });

  app.get("/v1/google/start", (req, res) => {
    const state = String(req.query.state ?? "");
    const expiresAt = connectionStates.get(state) ?? 0;
    if (expiresAt < Date.now()) return res.status(403).send("This connection link is invalid or expired.");
    res.redirect(driveArchive.getAuthorizationUrl(state));
  });

  app.get("/v1/google/callback", async (req, res, next) => {
    try {
      const state = String(req.query.state ?? "");
      const expiresAt = connectionStates.get(state) ?? 0;
      connectionStates.delete(state);
      if (expiresAt < Date.now()) return res.status(403).send("This connection request is invalid or expired.");
      if (typeof req.query.code !== "string") return res.status(400).send("Missing authorization code.");
      const tokens = await driveArchive.acceptCode(req.query.code);
      const refreshToken = typeof tokens.refresh_token === "string" ? tokens.refresh_token : "";
      const persistence = refreshToken && !config.GOOGLE_REFRESH_TOKEN
        ? `<h2>One final free-hosting step</h2>
           <p>Copy this refresh token into the Render environment variable <strong>GOOGLE_REFRESH_TOKEN</strong>. Do not send it in chat.</p>
           <input id="refresh" type="password" readonly value="${escapeHtml(refreshToken)}" style="width:100%;max-width:48rem;padding:.75rem" />
           <button onclick="navigator.clipboard.writeText(document.getElementById('refresh').value)">Copy token</button>
           <p>After saving the variable in Render, this token will survive free-server sleep and redeploys.</p>`
        : "<p>The Google Drive connection is ready and persistent.</p>";
      res.type("html").send(`<!doctype html><meta name="viewport" content="width=device-width"><title>Google Drive connected</title><main style="font-family:system-ui;max-width:52rem;margin:3rem auto;padding:1rem"><h1>Google Drive connected</h1>${persistence}<p>You may return to Bonnie Notes.</p></main>`);
    } catch (error) {
      next(error);
    }
  });

  app.post("/v1/process", requireAuth, upload.single("audio"), async (req, res, next) => {
    if (!req.file) return res.status(400).json({ error: "Audio file is required" });
    const title = String(req.body.title ?? "Untitled recording").slice(0, 160);
    const recordedAt = validIsoDate(req.body.recordedAt) ? req.body.recordedAt : new Date().toISOString();
    try {
      const result = await processor(req.file.path, { title, recordedAt });
      const payload = { id: String(req.body.id ?? crypto.randomUUID()), title, recordedAt, ...result };
      const drive = await driveArchive.uploadMeeting({
        title,
        recordedAt,
        audioPath: req.file.path,
        audioMime: req.file.mimetype || "audio/mp4",
        payload
      });
      res.json({ ...payload, drive });
    } catch (error) {
      next(error);
    } finally {
      await fs.unlink(req.file.path).catch(() => {});
    }
  });

  app.use((error, _req, res, _next) => {
    console.error(error);
    const status = error instanceof multer.MulterError ? 400 : 500;
    res.status(status).json({ error: status === 500 ? "Processing failed" : error.message });
  });

  return app;
}

function validIsoDate(value) {
  return typeof value === "string" && !Number.isNaN(Date.parse(value));
}

function escapeHtml(value) {
  return value.replace(/[&<>"']/g, (character) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    "\"": "&quot;",
    "'": "&#39;"
  })[character]);
}
