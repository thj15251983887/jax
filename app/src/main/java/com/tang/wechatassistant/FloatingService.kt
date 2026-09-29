package com.tang.wechatassistant

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat

class FloatingService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var bubble: TextView
    private var activeDialog: Dialog? = null
    private val main = Color.parseColor("#276EF1")
    private val ink = Color.parseColor("#172033")
    private val muted = Color.parseColor("#667085")
    private val surface = Color.parseColor("#F4F7FB")
    private val presets = mapOf(
        "客户" to listOf("高情商", "催付款", "不能降价", "约时间", "安排师傅", "说明延期", "不承认责任", "推进成交"),
        "朋友" to listOf("随和", "幽默", "婉拒", "答应", "安慰", "感谢", "约饭", "认真解释"),
        "老婆" to listOf("温柔", "哄一下", "先道歉", "表达关心", "解释原因", "晚点回家", "承诺改进", "避免争吵")
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(11, NotificationCompat.Builder(this, "assistant")
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("微信AI回复助手")
            .setContentText("悬浮球运行中").setOngoing(true).build())
        val filter = IntentFilter().apply {
            addAction("com.tang.wechatassistant.CAPTURE_REPLY")
            addAction("com.tang.wechatassistant.CAPTURE_FAILED")
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(receiver, filter)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        bubble = TextView(this).apply {
            text = "AI"; textSize = 17f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD; background = rounded(main, 60f)
            elevation = dp(8).toFloat()
        }
        val p = WindowManager.LayoutParams(dp(58), dp(58), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START; x = dp(12); y = dp(250)
        }
        var sx=0; var sy=0; var ix=0; var iy=0
        bubble.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx=e.rawX.toInt(); sy=e.rawY.toInt(); ix=p.x; iy=p.y; true }
                MotionEvent.ACTION_MOVE -> { p.x=ix+e.rawX.toInt()-sx; p.y=iy+e.rawY.toInt()-sy; wm.updateViewLayout(bubble,p); true }
                MotionEvent.ACTION_UP -> { if (kotlin.math.abs(e.rawX.toInt()-sx)<15 && kotlin.math.abs(e.rawY.toInt()-sy)<15) showPanel(); true }
                else -> false
            }
        }
        wm.addView(bubble, p)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.action == "com.tang.wechatassistant.CAPTURE_REPLY") showReply(i.getStringExtra("reply") ?: "未生成回复")
            else if (i?.action == "com.tang.wechatassistant.CAPTURE_FAILED") Toast.makeText(this@FloatingService,"你取消了本次屏幕读取",Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCapture() {
        activeDialog?.dismiss()
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, CaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }, 300)
    }

    private fun showPanel() {
        activeDialog?.dismiss()
        val prefs=getSharedPreferences("settings",MODE_PRIVATE)
        var relation=prefs.getString("relation","客户")?:"客户"
        val selected=linkedSetOf<String>().apply {
            addAll((prefs.getString("guideKeywords","")?:"").split("、").filter{it.isNotBlank()})
        }
        val dialog=Dialog(this)
        val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(18),dp(20),dp(22)); background=rounded(Color.WHITE,24f) }
        val titleRow=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        titleRow.addView(TextView(this).apply { text="AI 回复助手"; textSize=22f; setTextColor(ink); typeface=Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(0,-2,1f))
        titleRow.addView(textButton("关闭", false) { dialog.dismiss() })
        content.addView(titleRow)
        content.addView(TextView(this).apply { text="选择关系和关键词，生成更贴合的三条回复"; textSize=13f; setTextColor(muted); setPadding(0,dp(4),0,dp(14)) })

        content.addView(sectionTitle("关系模式"))
        val relationRow=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        val relationButtons=mutableMapOf<String,TextView>()
        val keywordRow=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; setPadding(0,dp(4),0,dp(4)) }
        fun refreshRelations(){ relationButtons.forEach { (name,v) -> styleChoice(v,name==relation) } }
        fun rebuildKeywords(){
            keywordRow.removeAllViews()
            presets[relation].orEmpty().forEach { word ->
                val chip=TextView(this).apply { text=word; textSize=14f; gravity=Gravity.CENTER; setPadding(dp(13),dp(9),dp(13),dp(9)) }
                fun paint(){ styleChoice(chip,selected.contains(word)) }
                paint(); chip.setOnClickListener { if(!selected.add(word)) selected.remove(word); paint() }
                keywordRow.addView(chip, LinearLayout.LayoutParams(-2,-2).apply { marginEnd=dp(8) })
            }
        }
        listOf("客户","朋友","老婆").forEach { name ->
            val b=TextView(this).apply { text=name; textSize=15f; gravity=Gravity.CENTER; setPadding(0,dp(11),0,dp(11)); setOnClickListener { relation=name; selected.clear(); refreshRelations(); rebuildKeywords() } }
            relationButtons[name]=b; relationRow.addView(b,LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd=dp(8) })
        }
        refreshRelations(); content.addView(relationRow)
        content.addView(sectionTitle("关键词引导").apply { setPadding(0,dp(16),0,dp(7)) })
        rebuildKeywords()
        content.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false; addView(keywordRow) })
        val extra=EditText(this).apply {
            hint="补充要求，例如：明天下午到现场，价格不能再降"
            setText(prefs.getString("guideExtra","")); textSize=15f; setTextColor(ink); setHintTextColor(Color.parseColor("#98A2B3"))
            minLines=2; maxLines=4; setPadding(dp(14),dp(11),dp(14),dp(11)); background=outlined()
        }
        content.addView(sectionTitle("补充要求").apply { setPadding(0,dp(16),0,dp(7)) }); content.addView(extra)
        val input=EditText(this).apply {
            hint="也可以直接粘贴聊天内容…"; textSize=15f; minLines=3; maxLines=6
            setPadding(dp(14),dp(11),dp(14),dp(11)); background=outlined()
        }
        content.addView(sectionTitle("聊天内容（可选）").apply { setPadding(0,dp(16),0,dp(7)) }); content.addView(input)
        val status=TextView(this).apply { textSize=14f; setTextColor(muted); gravity=Gravity.CENTER; setPadding(0,dp(10),0,0) }
        fun saveGuide(){
            prefs.edit().putString("relation",relation).putString("guideKeywords",selected.joinToString("、"))
                .putString("guideExtra",extra.text.toString().trim()).apply()
        }
        content.addView(primaryButton("读取当前屏幕并生成 3 条回复") { saveGuide(); startCapture() }, LinearLayout.LayoutParams(-1,dp(50)).apply { topMargin=dp(18) })
        content.addView(textButton("根据粘贴内容生成", true) {
            val chat=input.text.toString().trim()
            if(chat.isBlank()){ status.text="请先粘贴聊天内容，或使用读取屏幕"; return@textButton }
            saveGuide(); status.text="AI 正在生成…"
            Thread {
                val reply=AiApi.generate(this,chat,relation,selected.joinToString("、"),extra.text.toString().trim())
                    .getOrElse { localDraft(relation) + "\n\n联网失败：${it.message}" }
                Handler(Looper.getMainLooper()).post { showReply(reply) }
            }.start()
        }, LinearLayout.LayoutParams(-1,dp(46)).apply { topMargin=dp(8) })
        content.addView(status)
        val scroll=ScrollView(this).apply { addView(content) }
        dialog.setContentView(scroll); dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.setOnDismissListener { if(activeDialog===dialog) activeDialog=null }; dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels*0.94).toInt(),(resources.displayMetrics.heightPixels*0.86).toInt())
        activeDialog=dialog
    }

    private fun showReply(raw: String) {
        activeDialog?.dismiss()
        val relation=getSharedPreferences("settings",MODE_PRIVATE).getString("relation","客户")?:"客户"
        val dialog=Dialog(this)
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(18),dp(16),dp(18),dp(20)); background=rounded(surface,24f) }
        val head=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        head.addView(TextView(this).apply { text="$relation · 3条候选回复"; textSize=20f; setTextColor(ink); typeface=Typeface.DEFAULT_BOLD },LinearLayout.LayoutParams(0,-2,1f))
        head.addView(textButton("关闭",false){dialog.dismiss()}); box.addView(head)
        val replies=parseReplies(raw)
        replies.forEachIndexed { index, reply ->
            val card=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(15),dp(13),dp(15),dp(12)); background=rounded(Color.WHITE,18f); elevation=dp(2).toFloat() }
            card.addView(TextView(this).apply { text="方案 ${index+1}"; textSize=12f; setTextColor(main); typeface=Typeface.DEFAULT_BOLD })
            card.addView(TextView(this).apply { text=reply; textSize=16f; setTextColor(ink); setPadding(0,dp(7),0,dp(9)); setTextIsSelectable(true) })
            card.addView(textButton("复制这条回复",true){ copy(reply) },LinearLayout.LayoutParams(-1,dp(42)))
            box.addView(card,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(10) })
        }
        box.addView(sectionTitle("快捷微调").apply { setPadding(0,dp(16),0,dp(8)) })
        val refineRow=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        listOf("更简短","更温柔","更坚定","换种说法").forEach { directive ->
            refineRow.addView(textButton(directive,false){
                Toast.makeText(this,"正在重新生成…",Toast.LENGTH_SHORT).show()
                Thread {
                    val next=AiApi.refine(this,replies.joinToString("|||"),relation,directive).getOrElse { "生成失败：${it.message}" }
                    Handler(Looper.getMainLooper()).post { showReply(next) }
                }.start()
            },LinearLayout.LayoutParams(0,dp(42),1f).apply { marginEnd=dp(5) })
        }
        box.addView(refineRow)
        box.addView(primaryButton("返回重新选择") { dialog.dismiss(); showPanel() },LinearLayout.LayoutParams(-1,dp(48)).apply { topMargin=dp(14) })
        val scroll=ScrollView(this).apply { addView(box) }
        dialog.setContentView(scroll); dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.setOnDismissListener { if(activeDialog===dialog) activeDialog=null }; dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels*0.94).toInt(),(resources.displayMetrics.heightPixels*0.86).toInt())
        activeDialog=dialog
    }

    private fun parseReplies(raw:String):List<String>{
        val clean=raw.trim()
        val parts=clean.split("|||").map{it.trim().replace(Regex("^[1-3][.、：:]\\s*"),"")}.filter{it.isNotBlank()}
        if(parts.size>=2) return parts.take(3)
        val numbered=clean.split(Regex("(?m)^\\s*[1-3][.、：:]\\s*")).map{it.trim()}.filter{it.isNotBlank()}
        return if(numbered.size>=2) numbered.take(3) else listOf(clean)
    }
    private fun copy(text:String){
        (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText("AI回复",text))
        Toast.makeText(this,"已复制，可以粘贴发送",Toast.LENGTH_SHORT).show()
    }
    private fun sectionTitle(t:String)=TextView(this).apply { text=t; textSize=14f; setTextColor(ink); typeface=Typeface.DEFAULT_BOLD }
    private fun primaryButton(t:String,onClick:()->Unit)=Button(this).apply { text=t; textSize=16f; setTextColor(Color.WHITE); isAllCaps=false; background=rounded(main,16f); setOnClickListener{onClick()} }
    private fun textButton(t:String,accent:Boolean,onClick:()->Unit)=TextView(this).apply { text=t; textSize=14f; gravity=Gravity.CENTER; setTextColor(if(accent)main else muted); setPadding(dp(10),dp(7),dp(10),dp(7)); background=rounded(if(accent)Color.parseColor("#EAF1FF") else Color.TRANSPARENT,12f); setOnClickListener{onClick()} }
    private fun styleChoice(v:TextView,selected:Boolean){ v.setTextColor(if(selected)Color.WHITE else ink); v.background=rounded(if(selected)main else Color.parseColor("#E9EEF6"),14f) }
    private fun rounded(color:Int,radius:Float)=GradientDrawable().apply { shape=GradientDrawable.RECTANGLE; setColor(color); cornerRadius=radius }
    private fun outlined()=GradientDrawable().apply { setColor(Color.WHITE); cornerRadius=16f; setStroke(dp(1),Color.parseColor("#D0D5DD")) }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun localDraft(relation:String)=when(relation){
        "朋友"->"收到，我先看看怎么安排，晚点给你准信。|||行，我了解了，等我确认一下再跟你说。|||明白，我先处理手头的事，很快回复你。"
        "老婆"->"我知道你在意这个，是我考虑得不够周到，我会认真处理。|||别生气，我先把事情处理好，晚点认真跟你说。|||我明白你的感受，这件事我会放在心上，也会给你一个交代。"
        else->"我理解你的意思，这件事我也很重视，我先核实清楚后尽快回复你。|||收到，我马上确认具体情况，确认后第一时间给你答复。|||这个问题我们会积极处理，具体方案需要核实后再给你明确回复。"
    }
    private fun createChannel(){ if(Build.VERSION.SDK_INT>=26)(getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel("assistant","AI悬浮助手",NotificationManager.IMPORTANCE_LOW)) }
    override fun onDestroy(){ activeDialog?.dismiss(); activeDialog=null; try{unregisterReceiver(receiver)}catch(_:Exception){}; if(::bubble.isInitialized)try{wm.removeView(bubble)}catch(_:Exception){}; super.onDestroy() }
}
