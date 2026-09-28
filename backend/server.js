import express from "express";
import OpenAI from "openai";

const app = express();
app.use(express.json({ limit: "12mb" }));

const model = process.env.KIMI_MODEL || "kimi-k2.6";
const rules = `你是中文微信回复助手。根据对话上下文，只输出一条可以直接复制发送的中文回复正文，不解释。语气自然、有分寸、不卑不亢。用户常处理暖通、工程、报价、催款、客户投诉和商务沟通。涉及工程责任、赔偿或质量争议，在事实未核实前不要替用户承认法律责任、过错或具体赔偿金额。不要猜测屏幕中看不到的信息。`;

function kimiClient() {
  if (!process.env.KIMI_API_KEY) throw new Error("KIMI_API_KEY is not configured");
  return new OpenAI({
    apiKey: process.env.KIMI_API_KEY,
    baseURL: process.env.KIMI_BASE_URL || "https://api.moonshot.ai/v1",
  });
}

app.use((req, res, next) => {
  if (req.path === "/health" || !process.env.APP_TOKEN) return next();
  if (req.headers.authorization !== `Bearer ${process.env.APP_TOKEN}`) {
    return res.status(401).json({ error: "unauthorized" });
  }
  next();
});

app.post("/reply", async (req, res) => {
  try {
    const chat = String(req.body?.chat || "").slice(0, 12000);
    if (!chat.trim()) return res.status(400).json({ error: "chat required" });
    const result = await kimiClient().chat.completions.create({
      model,
      messages: [
        { role: "system", content: rules },
        { role: "user", content: `回复风格：${req.body?.style || "高情商"}\n聊天内容：\n${chat}` },
      ],
    });
    res.json({ reply: result.choices?.[0]?.message?.content || "" });
  } catch (error) {
    console.error(error);
    res.status(500).json({ error: error?.message || "Kimi request failed" });
  }
});

app.post("/reply-image", async (req, res) => {
  try {
    const imageBase64 = String(req.body?.imageBase64 || "");
    if (!imageBase64) return res.status(400).json({ error: "image required" });
    const result = await kimiClient().chat.completions.create({
      model,
      messages: [
        { role: "system", content: rules },
        { role: "user", content: [
          { type: "text", text: `这是用户主动截取的当前微信聊天屏幕。请优先读取左右两侧聊天气泡中的可见文字，按从上到下的顺序理解上下文；忽略状态栏、键盘、悬浮球、头像、时间和无关UI。若屏幕中包含照片、视频、表情包或文件消息，只把它们视为“对方/我发送了一条媒体消息”，不要猜测媒体内容，也不要因此放弃读取其余文字。根据最后一条可辨认的对方消息给出回复。回复风格：${req.body?.style || "高情商"}。只输出可直接发送的建议回复；如果确实没有任何可辨认文字，则输出“未识别到清晰的聊天文字，请改用手动粘贴”。` },
          { type: "image_url", image_url: { url: `data:image/jpeg;base64,${imageBase64}` } },
        ] },
      ],
    });
    res.json({ reply: result.choices?.[0]?.message?.content || "" });
  } catch (error) {
    console.error(error);
    res.status(500).json({ error: error?.message || "Kimi vision request failed" });
  }
});

app.get("/health", (_, res) => res.json({
  ok: true,
  provider: "kimi",
  model,
  configured: Boolean(process.env.KIMI_API_KEY),
}));
app.listen(process.env.PORT || 3000, () => console.log("Wechat AI V4 Kimi backend running"));
