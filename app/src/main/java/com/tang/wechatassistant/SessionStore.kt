package com.tang.wechatassistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object SessionStore {
    private const val KEY = "sessionsV7"
    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun all(context: Context): MutableList<JSONObject> {
        val raw = prefs(context).getString(KEY, "").orEmpty()
        val list = mutableListOf<JSONObject>()
        if (raw.isNotBlank()) runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) list.add(array.getJSONObject(i))
        }
        if (list.isEmpty()) {
            list.add(newObject("客户", "客户1"))
            list.add(newObject("朋友", "朋友1"))
            list.add(newObject("老婆", "老婆"))
            save(context, list)
        }
        return list
    }

    fun byCategory(context: Context, category: String) = all(context).filter { it.optString("category") == category }
    fun find(context: Context, id: String) = all(context).firstOrNull { it.optString("id") == id }

    fun create(context: Context, category: String, name: String): JSONObject {
        val list = all(context)
        val item = newObject(category, name.ifBlank { "$category${list.count { it.optString("category") == category } + 1}" })
        list.add(item); save(context, list); return item
    }

    fun rename(context: Context, id: String, name: String) = mutate(context, id) { it.put("name", name) }
    fun updateGuide(context: Context, id: String, keywords: String, extra: String) = mutate(context, id) {
        it.put("keywords", keywords).put("extra", extra)
    }

    fun append(context: Context, id: String, role: String, text: String) {
        if (text.isBlank()) return
        mutate(context, id) { session ->
            val history = session.optJSONArray("history") ?: JSONArray()
            val last = if (history.length() > 0) history.optJSONObject(history.length()-1) else null
            if (last?.optString("role") != role || last?.optString("text") != text) {
                history.put(JSONObject().put("role", role).put("text", text).put("time", System.currentTimeMillis()))
            }
            while (history.length() > 30) history.remove(0)
            session.put("history", history)
        }
    }

    fun removeTurn(context: Context, id: String, index: Int) = mutate(context, id) {
        val history=it.optJSONArray("history") ?: JSONArray()
        if(index in 0 until history.length()) history.remove(index)
        it.put("history",history)
    }

    fun clear(context: Context, id: String) = mutate(context, id) { it.put("history", JSONArray()) }

    fun historyText(session: JSONObject): String {
        val history=session.optJSONArray("history") ?: JSONArray()
        val lines=mutableListOf<String>()
        for(i in 0 until history.length()) {
            val turn=history.optJSONObject(i) ?: continue
            lines.add("${turn.optString("role")}：${turn.optString("text")}")
        }
        return lines.joinToString("\n").takeLast(8000)
    }

    private fun newObject(category: String, name: String) = JSONObject()
        .put("id", "${System.currentTimeMillis()}_${(1000..9999).random()}")
        .put("category", category).put("name", name).put("keywords", "").put("extra", "")
        .put("history", JSONArray())

    private fun mutate(context: Context, id: String, action: (JSONObject)->Unit) {
        val list=all(context); list.firstOrNull { it.optString("id") == id }?.let(action); save(context,list)
    }

    private fun save(context: Context, list: List<JSONObject>) {
        val array=JSONArray(); list.forEach { array.put(it) }
        prefs(context).edit().putString(KEY,array.toString()).apply()
    }
}
