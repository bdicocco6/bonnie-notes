import { z } from "zod";

const schema = z.object({
  PORT: z.coerce.number().int().positive().default(8787),
  PUBLIC_BASE_URL: z.string().url(),
  APP_UPLOAD_TOKEN: z.string().min(24),
  OPENAI_API_KEY: z.string().min(20),
  OPENAI_TASK_MODEL: z.string().default("gpt-5-mini"),
  GOOGLE_CLIENT_ID: z.string().min(5).optional(),
  GOOGLE_CLIENT_SECRET: z.string().min(5).optional(),
  GOOGLE_REDIRECT_URI: z.string().url().optional(),
  GOOGLE_REFRESH_TOKEN: z.string().min(20).optional(),
  GOOGLE_DRIVE_FOLDER_NAME: z.string().default("Bonnie Notes"),
  TOKEN_STORE_PATH: z.string().default("./data/google-token.json"),
  MAX_UPLOAD_MB: z.coerce.number().positive().max(500).default(200)
});

export function loadConfig(env = process.env) {
  const parsed = schema.safeParse(env);
  if (!parsed.success) {
    throw new Error(`Invalid configuration: ${parsed.error.message}`);
  }
  return parsed.data;
}
