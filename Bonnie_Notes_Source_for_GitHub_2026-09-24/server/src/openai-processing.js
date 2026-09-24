import fs from "node:fs";
import OpenAI from "openai";

export function createOpenAIProcessor({ apiKey, taskModel }) {
  const client = new OpenAI({ apiKey });

  return async function processRecording(filePath, metadata) {
    const diarized = await client.audio.transcriptions.create({
      file: fs.createReadStream(filePath),
      model: "gpt-4o-transcribe-diarize",
      response_format: "diarized_json",
      chunking_strategy: "auto"
    });

    const segments = normalizeSegments(diarized.segments ?? []);
    const transcript = diarized.text || segments
      .map((segment) => `${segment.speaker}: ${segment.text}`)
      .join("\n");

    const extracted = await client.responses.create({
      model: taskModel,
      instructions: [
        "Extract only tasks and deadlines explicitly supported by the transcript.",
        "Do not invent an owner or date. Use null when the transcript does not specify one.",
        "Resolve relative dates only when the recording date makes the result unambiguous.",
        "Include a short exact source quote for verification."
      ].join(" "),
      input: `Recording title: ${metadata.title}\nRecording date: ${metadata.recordedAt}\n\n${transcript}`,
      text: {
        format: {
          type: "json_schema",
          name: "meeting_actions",
          strict: true,
          schema: {
            type: "object",
            additionalProperties: false,
            properties: {
              tasks: {
                type: "array",
                items: {
                  type: "object",
                  additionalProperties: false,
                  properties: {
                    description: { type: "string" },
                    owner: { type: ["string", "null"] },
                    dueDate: { type: ["string", "null"], description: "ISO 8601 date when known" },
                    sourceQuote: { type: "string" }
                  },
                  required: ["description", "owner", "dueDate", "sourceQuote"]
                }
              }
            },
            required: ["tasks"]
          }
        }
      }
    });

    return {
      transcript,
      segments,
      tasks: JSON.parse(extracted.output_text).tasks
    };
  };
}

export function normalizeSegments(segments) {
  return segments.map((segment, index) => ({
    id: index,
    speaker: String(segment.speaker ?? "Speaker"),
    startSeconds: Number(segment.start ?? 0),
    endSeconds: Number(segment.end ?? segment.start ?? 0),
    text: String(segment.text ?? "").trim()
  }));
}

