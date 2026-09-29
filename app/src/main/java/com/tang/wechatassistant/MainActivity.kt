package com.tang.wechatassistant

import android.app.*
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),100)
        val prefs=getSharedPreferences("settings",MODE_PRIVATE)
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(48,60,48,48) }
        box.addView(TextView(this).apply { text="微信 AI 回复助手 V4"+System.lineSeparator()+System.lineSeparator()+"配置一次服务器后即可使用悬浮球。支持手动粘贴，也支持你主动授权后读取当前屏幕。"; textSize=18f })
        val endpoint=EditText(this).apply { hint="HTTPS 后端地址，例如 https://ai.example.com"; setText(prefs.getString("endpoint","")) }
        val token=EditText(this).apply { hint="访问口令（可选）"; setText(prefs.getString("token","")) }
        val kimiKey=EditText(this).apply { hint="Kimi API Key（必填，仅保存在本机）"; inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD; setText(prefs.getString("kimiKey","")) }
        box.addView(endpoint); box.addView(token); box.addView(kimiKey)
        box.addView(Button(this).apply { text="保存配置"; setOnClickListener { prefs.edit().putString("endpoint",endpoint.text.toString().trim()).putString("token",token.text.toString().trim()).putString("kimiKey",kimiKey.text.toString().trim()).apply(); Toast.makeText(this@MainActivity,"配置已保存",Toast.LENGTH_SHORT).show() } })
        box.addView(Button(this).apply { text="开启悬浮球"; setOnClickListener { startBubble() } })
        box.addView(Button(this).apply { text="关闭悬浮球"; setOnClickListener { stopService(Intent(this@MainActivity,FloatingService::class.java)) } })
        setContentView(box)
    }
    private fun startBubble(){
        if(!Settings.canDrawOverlays(this)){ startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))); Toast.makeText(this,"请允许显示在其他应用上层，然后返回再点一次",Toast.LENGTH_LONG).show(); return }
        startForegroundService(Intent(this,FloatingService::class.java)); Toast.makeText(this,"悬浮球已开启",Toast.LENGTH_SHORT).show()
    }
}
