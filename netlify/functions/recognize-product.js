export default async (req) => {
  const headers = {
    "Content-Type": "application/json",
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "Content-Type",
    "Access-Control-Allow-Methods": "POST, OPTIONS"
  };
  if (req.method === "OPTIONS") return new Response("", { status: 204, headers });
  if (req.method !== "POST") return new Response(JSON.stringify({ error: "Method not allowed" }), { status: 405, headers });

  const apiKey = Netlify.env.get("GEMINI_API_KEY");
  if (!apiKey) return new Response(JSON.stringify({ error: "API Key Gemini belum dipasang di Netlify." }), { status: 503, headers });

  try {
    const body = await req.json();
    const image = body.image;
    const candidates = Array.isArray(body.candidates) ? body.candidates.slice(0, 12) : [];
    if (!image || !image.data || !image.mimeType || !candidates.length) {
      return new Response(JSON.stringify({ error: "Foto target dan katalog foto produk diperlukan." }), { status: 400, headers });
    }
    const parts = [{
      text: "Cocokkan foto target produk dengan foto-foto katalog. Foto katalog diberi label INDEX dan NAMA. Jangan gunakan pengetahuan merek dari luar katalog; pilih hanya jika kemasan terlihat cocok. Jika ragu atau tidak cocok, index=-1. Balas JSON saja dengan bentuk {\"index\": integer, \"confidence\": number, \"reason\": string}. Confidence 0 sampai 1."
    }];
    for (let i = 0; i < candidates.length; i++) {
      const item = candidates[i];
      if (!item.photo || !item.photo.startsWith("data:image/")) continue;
      const match = item.photo.match(/^data:(image\\/(?:jpeg|png|webp));base64,(.+)$/);
      if (!match) continue;
      parts.push({ text: "KATALOG INDEX " + i + ", NAMA " + String(item.name || "").slice(0, 100) });
      parts.push({ inlineData: { mimeType: match[1], data: match[2] } });
    }
    parts.push({ text: "FOTO TARGET yang sedang dipindai:" });
    parts.push({ inlineData: { mimeType: image.mimeType, data: image.data } });

    const response = await fetch("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent", {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-goog-api-key": apiKey },
      body: JSON.stringify({
        contents: [{ role: "user", parts }],
        generationConfig: { responseMimeType: "application/json", temperature: 0 }
      })
    });
    const result = await response.json();
    if (!response.ok) {
      return new Response(JSON.stringify({ error: result.error?.message || "Gemini API gagal." }), { status: 502, headers });
    }
    const text = result.candidates?.[0]?.content?.parts?.map(p => p.text || "").join("") || "";
    const parsed = JSON.parse(text.match(/\\{[\\s\\S]*\\}/)?.[0] || text);
    const index = Number(parsed.index);
    const confidence = Number(parsed.confidence);
    if (!Number.isInteger(index) || index < 0 || index >= candidates.length || !Number.isFinite(confidence)) {
      return new Response(JSON.stringify({ index: -1, confidence: 0, reason: "Produk tidak cocok dengan katalog." }), { status: 200, headers });
    }
    return new Response(JSON.stringify({ index, confidence, reason: String(parsed.reason || "") }), { status: 200, headers });
  } catch (error) {
    return new Response(JSON.stringify({ error: error.message || "Gagal memproses foto." }), { status: 500, headers });
  }
};
