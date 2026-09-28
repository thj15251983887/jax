package com.tang.wechatassistant

import android.app.Service
import android.app.Dialog
import android.content.*
import android.graphics.PixelFormat
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.view.*
import android.widget.*

class FloatingService: Service(){
    private lateinit var wm:WindowManager; private lateinit var bubble:TextView
    override fun onBind(i:Intent?):IBinder?=null
    override fun onCreate(){ super.onCreate(); createChannel(); startForeground(11, NotificationCompat.Builder(this,"assistant").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("微信AI回复助手").setContentText("悬浮球运行中").setOngoing(true).build()); val filter=android.content.IntentFilter().apply { addAction("com.tang.wechatassistant.CAPTURE_REPLY"); addAction("com.tang.wechatassistant.CAPTURE_FAILED") }; if(Build.VERSION.SDK_INT>=33) registerReceiver(receiver,filter,RECEIVER_NOT_EXPORTED) else @Suppress("DEPRECATION") registerReceiver(receiver,filter); wm=getSystemService(WINDOW_SERVICE) as WindowManager
        bubble=TextView(this).apply { text="AI"; textSize=18f; gravity=Gravity.CENTER; setBackgroundResource(android.R.drawable.btn_default); setPadding(18,18,18,18) }
        val p=WindowManager.LayoutParams(120,120,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT).apply { gravity=Gravity.TOP or Gravity.START; x=20;y=500 }
        var sx=0;var sy=0;var ix=0;var iy=0
        bubble.setOnTouchListener { _,e -> when(e.action){ MotionEvent.ACTION_DOWN->{sx=e.rawX.toInt();sy=e.rawY.toInt();ix=p.x;iy=p.y;true}; MotionEvent.ACTION_MOVE->{p.x=ix+(e.rawX.toInt()-sx);p.y=iy+(e.rawY.toInt()-sy);wm.updateViewLayout(bubble,p);true}; MotionEvent.ACTION_UP->{if(kotlin.math.abs(e.rawX.toInt()-sx)<15 && kotlin.math.abs(e.rawY.toInt()-sy)<15) showPanel();true};else->false } }
        wm.addView(bubble,p)
    }
    private val receiver=object:BroadcastReceiver(){ override fun onReceive(c:Context?,i:Intent?){ if(i?.action=="com.tang.wechatassistant.CAPTURE_REPLY") showReply(i.getStringExtra("reply")?:"未生成回复") else if(i?.action=="com.tang.wechatassistant.CAPTURE_FAILED") Toast.makeText(this@FloatingService,"你取消了本次屏幕读取",Toast.LENGTH_SHORT).show() } }
    private fun startCapture(){ startActivity(Intent(this,CaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    private fun showReply(text:String){ val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(28,28,28,28);setBackgroundColor(0xFFF7F7F7.toInt())}; box.addView(TextView(this).apply{this.text="AI根据当前屏幕建议：\n\n$text";textSize=17f}); box.addView(Button(this).apply{this.text="复制";setOnClickListener{(getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText("AI回复",text));Toast.makeText(this@FloatingService,"已复制",Toast.LENGTH_SHORT).show()}}); Dialog(this).apply{setContentView(box);window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);show()}}
    private fun createChannel(){ if(Build.VERSION.SDK_INT>=26){ val nm=getSystemService(NOTIFICATION_SERVICE) as NotificationManager; nm.createNotificationChannel(NotificationChannel("assistant","AI悬浮助手",NotificationManager.IMPORTANCE_LOW)) } }
    private fun showPanel(){
        val panel=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(28,28,28,28); setBackgroundColor(0xFFF7F7F7.toInt()) }
        val input=EditText(this).apply { hint="粘贴对方的微信聊天内容…"; minLines=5 }
        val result=TextView(this).apply { text="选择回复风格后，这里显示建议。\n\n当前演示版先完成悬浮交互；联网 AI 接口放在下一步接入。"; textSize=16f; setPadding(0,20,0,20) }
        panel.addView(TextView(this).apply { text="AI 回复助手";textSize=21f }); panel.addView(Button(this).apply { text="读取当前屏幕并生成回复"; setOnClickListener { startCapture() } }); panel.addView(input)
        val row=LinearLayout(this)
        listOf("高情商","简短","商务强硬").forEach { style -> row.addView(Button(this).apply { text=style; setOnClickListener {
                val chat=input.text.toString()
                if(chat.isBlank()){ result.text="请先粘贴聊天内容。"; return@setOnClickListener }
                result.text="AI 正在生成…"
                Thread {
                    val text=AiApi.generate(this@FloatingService,chat,style).getOrElse { "联网失败：${it.message}\n\n本地建议：${localDraft(chat,style)}" }
                    Handler(Looper.getMainLooper()).post { result.text=text }
                }.start()
            } },LinearLayout.LayoutParams(0,-2,1f)) }
        panel.addView(row);panel.addView(result)
        panel.addView(Button(this).apply { text="复制回复";setOnClickListener { (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText("AI回复",result.text));Toast.makeText(this@FloatingService,"已复制",Toast.LENGTH_SHORT).show() } })
        val dialog=Dialog(this); dialog.setContentView(panel); dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY); dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.WRAP_CONTENT); dialog.show()
    }
    private fun localDraft(s:String,style:String):String { if(s.isBlank()) return "请先粘贴聊天内容。"; return when(style){"简短"->"收到，我先核实一下具体情况，确认后马上回复你。";"商务强硬"->"这个事情我们会积极处理，但具体责任和方案需要依据实际情况确认，确认清楚后我给你明确答复。";else->"我理解你的意思，这个事情我也比较重视。我先把具体情况核实清楚，该配合处理的我们一定积极配合，确认后我尽快给你一个明确回复。"} }
    override fun onDestroy(){ try{unregisterReceiver(receiver)}catch(_:Exception){}; if(::bubble.isInitialized) try{wm.removeView(bubble)}catch(_:Exception){};super.onDestroy() }
}
