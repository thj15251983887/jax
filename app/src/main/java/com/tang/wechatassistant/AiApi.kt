package com.tang.wechatassistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AiApi {
    private const val API_URL = "https://api.moonshot.cn/v1/chat/completions"
    private const val MODEL = "kimi-k2.6"
    private const val RULES = "你是中文微信回复助手。根据对话上下文，只输出一条可以直接复制发送的中文回复正文，不解释。语气自然、有分寸、不卑不亢。用户常处理暖通、工程、报价、催款、客户投诉和商务沟通。涉及工程责任、赔偿或质量争议，在事实未核实前不要替用户承认法律责任、过错或具体赔偿金额。不要猜测屏幕中看不到的信息。"

    private fun post(context: Context, userContent: Any): Result<String> = runCatching {
        val key = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString("kimiKey", "")?.trim().orEmpty()
        require(key.isNotBlank()) { "请先在主界面填写 Kimi API Key" }
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", RULES))
            .put(JSONObject().put("role", "user").put("content", userContent))
        val body = JSONObject().put("model", MODEL).put("messages", messages)
        val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $key")
        }
        connection.outputStream.use { it.write(body.toString().toByteArray()) }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val raw = stream.bufferedReader().use { it.readText() }
        if (connection.responseCode !in 200..299) error("Kimi 返回 ${connection.responseCode}: $raw")
        JSONObject(raw).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
    }

    private fun request(relation: String, keywords: String, extra: String, chat: String) =
        "关系模式：$relation\n关键词引导：${keywords.ifBlank { "无" }}\n补充要求：${extra.ifBlank { "无" }}\n聊天内容：\n$chat\n\n请生成3条可以直接发送的中文回复，三条表达要有明显区别。不要解释，不要编号，只用|||分隔三条回复。"

    fun generate(context: Context, chat: String, relation: String, keywords: String, extra: String): Result<String> =
        post(context, request(relation, keywords, extra, chat))

    fun generateFromImage(context: Context, imageBase64: String, relation: String, keywords: String, extra: String): Result<String> {
        val instruction = "这是用户主动截取的当前微信聊天屏幕。请优先读取左右两侧聊天气泡中的可见文字，按从上到下的顺序理解上下文；忽略状态栏、键盘、悬浮球、头像、时间和无关UI。若屏幕中包含照片、视频、表情包或文件消息，只把它们视为媒体消息，不要猜测媒体内容，也不要因此放弃读取其余文字。根据最后一条可辨认的对方消息生成回复。如果确实没有任何可辨认文字，则输出“未识别到清晰的聊天文字，请改用手动粘贴”。\n\n" + request(relation, keywords, extra, "以图片中的聊天内容为准")
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", instruction))
            .put(JSONObject().put("type", "image_url").put("image_url",
                JSONObject().put("url", "data:image/jpeg;base64,$imageBase64")))
        return post(context, content)
    }

    fun refine(context: Context, replies: String, relation: String, directive: String): Result<String> =
        post(context, "关系模式：$relation\n现有候选回复：\n$replies\n\n请按“$directive”重新生成3条可直接发送的回复。不要解释，不要编号，只用|||分隔。")
}
