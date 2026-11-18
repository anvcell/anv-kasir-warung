export default {
  async fetch(request, env) {
    const origin = request.headers.get("Origin") || "";
    const allowedOrigin = "https://anv-kasir-warung-2bcea.web.app";
    const corsOrigin = origin === allowedOrigin ? origin : allowedOrigin;
    const headers = {
      "Content-Type": "application/json; charset=utf-8",
      "Access-Control-Allow-Origin": corsOrigin,
      "Access-Control-Allow-Methods": "POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type",
      "Vary": "Origin"
    };
    const json = (data, status = 200) =>
      new Response(JSON.stringify(data), { status, headers });

    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers });
    if (request.method !== "POST") return json({ error: "Gunakan metode POST." }, 405);
    if (!env.GEMINI_API_KEY) return json({ error: "Secret GEMINI_API_KEY belum dipasang di Cloudflare." }, 503);

    try {
      const body = await request.json();
      const image = body.image;
      const candidates = Array.isArray(body.candidates) ? body.candidates.slice(0, 12) : [];
      if (!image?.data || !image?.mimeType || !candidates.length) {
        return json({ error: "Foto target dan katalog foto produk diperlukan." }, 400);
      }

      const parts = [{
        text: 'Cocokkan foto target produk dengan foto-foto katalog. Foto katalog diberi label INDEX dan NAMA. Pilih hanya jika kemasan terlihat cocok. Jika ragu atau tidak cocok, index=-1. Balas JSON saja dengan bentuk {"index": integer, "confidence": number, "reason": string}. Confidence 0 sampai 1.'
      }];

      for (let i = 0; i < candidates.length; i++) {
        const item = candidates[i];
        if (!item.photo || !item.photo.startsWith("data:image/")) continue;
        const match = item.photo.match(/^data:(image\/(?:jpeg|png|webp));base64,(.+)$/);
        if (!match) continue;
        parts.push({ text: "KATALOG INDEX " + i + ", NAMA " + String(item.name || "").slice(0, 100) });
        parts.push({ inlineData: { mimeType: match[1], data: match[2] } });
      }

      parts.push({ text: "FOTO TARGET yang sedang dipindai:" });
      parts.push({ inlineData: { mimeType: image.mimeType, data: image.data } });

      const response = await fetch("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent", {
        method: "POST",
        headers: { "Content-Type": "application/json", "x-goog-api-key": env.GEMINI_API_KEY },
        body: JSON.stringify({
          contents: [{ role: "user", parts }],
          generationConfig: { responseMimeType: "application/json", temperature: 0 }
        })
      });

      const result = await response.json().catch(() => ({}));
      if (!response.ok) return json({ error: result.error?.message || "Gemini API gagal memproses foto." }, 502);

      const output = result.candidates?.[0]?.content?.parts?.map(p => p.text || "").join("") || "";
      let parsed;
      try { parsed = JSON.parse(output); }
      catch { return json({ error: "Jawaban AI tidak berbentuk JSON. Coba foto ulang." }, 502); }

      const index = Number(parsed.index);
      const confidence = Number(parsed.confidence);
      if (!Number.isInteger(index) || index < 0 || index >= candidates.length || !Number.isFinite(confidence)) {
        return json({ index: -1, confidence: 0, reason: "Produk tidak cocok dengan katalog." });
      }
      return json({ index, confidence, reason: String(parsed.reason || "") });
    } catch (error) {
      return json({ error: error?.message || "Gagal memproses foto." }, 500);
    }
  }
};
