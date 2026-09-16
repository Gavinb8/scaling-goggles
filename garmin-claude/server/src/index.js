import "dotenv/config";
import express from "express";

const PORT = process.env.PORT || 8787;
const API_KEY = process.env.ANTHROPIC_API_KEY;
const DEFAULT_MODEL = process.env.CLAUDE_MODEL || "claude-sonnet-5";
const SHARED_SECRET = process.env.BRIDGE_SHARED_SECRET || "";

if (!API_KEY) {
  console.error(
    "ANTHROPIC_API_KEY is not set. Copy server/.env.example to server/.env and fill it in."
  );
  process.exit(1);
}

const app = express();
app.use(express.json({ limit: "1mb" }));

/**
 * Optional bridge between the AskClaude Android companion app and the
 * Claude API. Only worth running if you'd rather the API key live on a PC
 * / home server than on your phone -- otherwise the Android app can call
 * Anthropic directly (see android/app/.../ClaudeApiClient.kt) and this
 * service isn't needed at all.
 */
app.post("/ask", async (req, res) => {
  if (SHARED_SECRET) {
    const provided = req.get("x-bridge-secret");
    if (provided !== SHARED_SECRET) {
      return res.status(401).json({ error: "unauthorized" });
    }
  }

  const { prompt, history = [], model } = req.body || {};
  if (!prompt || typeof prompt !== "string") {
    return res.status(400).json({ error: "missing 'prompt' string" });
  }

  const messages = [
    ...history.map((turn) => ({ role: turn.role, content: turn.content })),
    { role: "user", content: prompt },
  ];

  try {
    const response = await fetch("https://api.anthropic.com/v1/messages", {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-api-key": API_KEY,
        "anthropic-version": "2023-06-01",
      },
      body: JSON.stringify({
        model: model || DEFAULT_MODEL,
        max_tokens: 1024,
        messages,
      }),
    });

    const data = await response.json();

    if (!response.ok) {
      const message = data?.error?.message || JSON.stringify(data);
      return res.status(response.status).json({ error: message });
    }

    const text = (data.content || [])
      .filter((block) => block.type === "text")
      .map((block) => block.text)
      .join("");

    res.json({ text });
  } catch (err) {
    console.error("Claude request failed:", err);
    res.status(502).json({ error: String(err) });
  }
});

app.get("/health", (_req, res) => res.json({ ok: true }));

app.listen(PORT, () => {
  console.log(`AskClaude bridge server listening on http://0.0.0.0:${PORT}`);
});
