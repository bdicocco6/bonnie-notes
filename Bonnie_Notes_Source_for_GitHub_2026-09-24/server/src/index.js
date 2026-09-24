import { createApp } from "./app.js";
import { loadConfig } from "./config.js";
import { GoogleDriveArchive } from "./google-drive.js";
import { createOpenAIProcessor } from "./openai-processing.js";

const config = loadConfig();
const app = createApp({
  config,
  processor: createOpenAIProcessor({ apiKey: config.OPENAI_API_KEY, taskModel: config.OPENAI_TASK_MODEL }),
  driveArchive: new GoogleDriveArchive(config)
});

app.listen(config.PORT, () => {
  console.log(`Bonnie Notes server listening on port ${config.PORT}`);
});

