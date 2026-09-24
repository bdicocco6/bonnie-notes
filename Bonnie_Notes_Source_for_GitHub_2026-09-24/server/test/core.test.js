import test from "node:test";
import assert from "node:assert/strict";
import { normalizeSegments } from "../src/openai-processing.js";
import { safeName } from "../src/google-drive.js";

test("normalizes diarized segments", () => {
  assert.deepEqual(normalizeSegments([{ speaker: "A", start: 1.2, end: 2.5, text: " Hello " }]), [{
    id: 0,
    speaker: "A",
    startSeconds: 1.2,
    endSeconds: 2.5,
    text: "Hello"
  }]);
});

test("makes Drive-safe names", () => {
  assert.equal(safeName("2026/09/24: Board * Meeting?"), "2026-09-24- Board - Meeting-");
});

