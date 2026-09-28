package com.tang.wechatassistant
import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
object AiApi {
    private fun post(context:Context,path:String,body:JSONObject):Result<String> = runCatching {
        val p=context.getSharedPreferences("settings",Context.MODE_PRIVATE)
        val endpoint=(p.getString("endpoint","")?:"").trimEnd('/')
        val token=p.getString("token","")?:""
        require(endpoint.startsWith("https://")){"请先在主界面配置 HTTPS 后端地址"}
        val c=(URL(endpoint+path).openConnection() as HttpURLConnection).apply{requestMethod="POST";connectTimeout=12000;readTimeout=45000;doOutput=true;setRequestProperty("Content-Type","application/json; charset=utf-8");if(token.isNotBlank())setRequestProperty("Authorization","Bearer $token")}
        c.outputStream.use{it.write(body.toString().toByteArray())}; val s=if(c.responseCode in 200..299)c.inputStream else c.errorStream; val raw=s.bufferedReader().use{it.readText()}; if(c.responseCode !in 200..299) error("服务返回 ${c.responseCode}: $raw"); JSONObject(raw).getString("reply")
    }
    fun generate(context:Context,chat:String,style:String)=post(context,"/reply",JSONObject().put("chat",chat).put("style",style))
    fun generateFromImage(context:Context,imageBase64:String,style:String)=post(context,"/reply-image",JSONObject().put("imageBase64",imageBase64).put("style",style))
}
