import express from "express";

const app = express();
app.use(express.json({ limit: "30mb" }));

const PORT = process.env.PORT || 8787;
const KEY = process.env.OPENAI_API_KEY;

app.get("/health", (_, res) => res.json({ ok: true, service: "StudyLens" }));

app.post("/analyze", async (req, res) => {
  try {
    if (!KEY) return res.status(500).json({ error: "Server AI credentials are not configured." });

    const { image, subject = "General", language = "English" } = req.body || {};
    if (!image) return res.status(400).json({ error: "Image is required." });

    const prompt = `You are StudyLens, a school study-note generator.
Subject: ${subject}
Output language: ${language}

Read the textbook page. Identify the meaningful headings/topics and produce concise exam-focused revision notes.

Return ONLY JSON:
{"topics":[{"title":"Topic","notes":["point","definition/formula/example"]}]}

Rules:
- Never invent information.
- Keep definitions, formulas, units and important examples accurate.
- Prioritize material a student should revise.
- Explain difficult wording simply.
- Summarize readable diagrams and tables when useful.
- Keep each point concise.`;

    const r = await fetch("https://api.openai.com/v1/responses", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${KEY}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        model: "gpt-5.6-luna",
        input: [{
          role: "user",
          content: [
            { type: "input_text", text: prompt },
            { type: "input_image", image_url: image, detail: "high" }
          ]
        }]
      })
    });

    const data = await r.json();
    if (!r.ok)
      return res.status(r.status).json({ error: data?.error?.message || "AI request failed." });

    let output = "";
    for (const item of data.output || [])
      for (const c of item.content || [])
        if (c.type === "output_text") output += c.text || "";

    output = output.replace(/```json/g, "").replace(/```/g, "").trim();
    res.json(JSON.parse(output));
  } catch (e) {
    res.status(500).json({ error: e.message || "Server error." });
  }
});

app.listen(PORT, () => console.log(`StudyLens backend running on ${PORT}`));
