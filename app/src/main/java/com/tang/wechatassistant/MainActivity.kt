package com.tang.wechatassistant

import android.app.*
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
        val prefs=getSharedPreferences("settings",MODE_PRIVATE)
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(48,60,48,48) }
        box.addView(TextView(this).apply { text="微信 AI 回复助手 V4\n\n配置一次服务器后即可使用悬浮球。支持手动粘贴，也支持你主动授权后读取当前屏幕。"; textSize=18f })
        val endpoint=EditText(this).apply { hint="HTTPS 后端地址，例如 https://ai.example.com"; setText(prefs.getString("endpoint","")) }
        val token=EditText(this).apply { hint="访问口令（可选）"; setText(prefs.getString("token","")) }
        box.addView(endpoint); box.addView(token)
        box.addView(Button(this).apply { text="保存配置"; setOnClickListener { prefs.edit().putString("endpoint",endpoint.text.toString().trim()).putString("token",token.text.toString().trim()).apply(); Toast.makeText(this@MainActivity,"配置已保存",Toast.LENGTH_SHORT).show() } })
        box.addView(Button(this).apply { text="开启悬浮球"; setOnClickListener { startBubble() } })
        box.addView(Button(this).apply { text="关闭悬浮球"; setOnClickListener { stopService(Intent(this@MainActivity,FloatingService::class.java)) } })
        setContentView(box)
    }
    private fun startBubble(){
        if(!Settings.canDrawOverlays(this)){ startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))); Toast.makeText(this,"请允许显示在其他应用上层，然后返回再点一次",Toast.LENGTH_LONG).show(); return }
        startForegroundService(Intent(this,FloatingService::class.java)); Toast.makeText(this,"悬浮球已开启",Toast.LENGTH_SHORT).show()
    }
}
