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
import org.json.JSONObject

class FloatingService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var bubble: TextView
    private var activeDialog: Dialog? = null
    private var currentSessionId = ""
    private val main=Color.parseColor("#276EF1"); private val ink=Color.parseColor("#172033")
    private val muted=Color.parseColor("#667085"); private val surface=Color.parseColor("#F4F7FB")
    private val presets=mapOf(
        "客户" to listOf("高情商","催付款","不能降价","约时间","安排师傅","说明延期","不承认责任","推进成交"),
        "朋友" to listOf("随和","幽默","婉拒","答应","安慰","感谢","约饭","认真解释"),
        "老婆" to listOf("温柔","哄一下","先道歉","表达关心","解释原因","晚点回家","承诺改进","避免争吵")
    )

    override fun onBind(intent:Intent?):IBinder?=null
    override fun onCreate(){
        super.onCreate(); createChannel()
        startForeground(11,NotificationCompat.Builder(this,"assistant").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("微信AI回复助手").setContentText("悬浮球运行中").setOngoing(true).build())
        wm=getSystemService(WINDOW_SERVICE) as WindowManager
        bubble=TextView(this).apply { text="AI";textSize=17f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;typeface=Typeface.DEFAULT_BOLD;background=rounded(main,60f);elevation=dp(8).toFloat() }
        val p=WindowManager.LayoutParams(dp(58),dp(58),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.START;x=dp(12);y=dp(250)}
        var sx=0;var sy=0;var ix=0;var iy=0
        bubble.setOnTouchListener{_,e->when(e.action){
            MotionEvent.ACTION_DOWN->{sx=e.rawX.toInt();sy=e.rawY.toInt();ix=p.x;iy=p.y;true}
            MotionEvent.ACTION_MOVE->{p.x=ix+e.rawX.toInt()-sx;p.y=iy+e.rawY.toInt()-sy;wm.updateViewLayout(bubble,p);true}
            MotionEvent.ACTION_UP->{if(kotlin.math.abs(e.rawX.toInt()-sx)<15&&kotlin.math.abs(e.rawY.toInt()-sy)<15)showPanel();true}
            else->false}}
        wm.addView(bubble,p)
    }

    private fun showPanel(){
        activeDialog?.dismiss()
        val prefs=getSharedPreferences("settings",MODE_PRIVATE)
        var category=prefs.getString("relation","客户")?:"客户"
        val available=SessionStore.byCategory(this,category)
        val savedId=prefs.getString("currentSession_$category","").orEmpty()
        currentSessionId=available.firstOrNull{it.optString("id")==savedId}?.optString("id") ?: available.first().optString("id")
        val session=SessionStore.find(this,currentSessionId) ?: available.first()
        val selected=linkedSetOf<String>().apply{addAll(session.optString("keywords").split("、").filter{it.isNotBlank()})}
        val dialog=Dialog(this)
        val content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(18),dp(20),dp(22));background=rounded(Color.WHITE,24f)}
        val head=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        head.addView(TextView(this).apply{text="AI 回复助手";textSize=22f;setTextColor(ink);typeface=Typeface.DEFAULT_BOLD},LinearLayout.LayoutParams(0,-2,1f))
        head.addView(textButton("关闭",false){dialog.dismiss()});content.addView(head)
        content.addView(TextView(this).apply{text="每位联系人独立保存上下文和历史记录";textSize=13f;setTextColor(muted);setPadding(0,dp(4),0,dp(14))})
        content.addView(sectionTitle("关系分类"))
        val categoryRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        listOf("客户","朋友","老婆").forEach{name->
            val b=TextView(this).apply{text=name;textSize=15f;gravity=Gravity.CENTER;setPadding(0,dp(11),0,dp(11));styleChoice(this,name==category);setOnClickListener{
                prefs.edit().putString("relation",name).apply();dialog.dismiss();showPanel()
            }}
            categoryRow.addView(b,LinearLayout.LayoutParams(0,-2,1f).apply{marginEnd=dp(8)})
        }
        content.addView(categoryRow)
        content.addView(sectionTitle("当前联系人").apply{setPadding(0,dp(15),0,dp(7))})
        val contactRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        SessionStore.byCategory(this,category).forEach{s->
            val id=s.optString("id");val chip=TextView(this).apply{text=s.optString("name");textSize=14f;gravity=Gravity.CENTER;setPadding(dp(13),dp(9),dp(13),dp(9));styleChoice(this,id==currentSessionId);setOnClickListener{
                prefs.edit().putString("currentSession_$category",id).apply();dialog.dismiss();showPanel()
            }}
            contactRow.addView(chip,LinearLayout.LayoutParams(-2,-2).apply{marginEnd=dp(8)})
        }
        contactRow.addView(textButton("＋新建",true){promptName("新建$category","$category${SessionStore.byCategory(this,category).size+1}"){name->
            val created=SessionStore.create(this,category,name);prefs.edit().putString("currentSession_$category",created.optString("id")).apply();dialog.dismiss();showPanel()
        }})
        content.addView(HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;addView(contactRow)})
        val manageRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        manageRow.addView(textButton("编辑名称",false){promptName("编辑联系人名称",session.optString("name")){name->SessionStore.rename(this,currentSessionId,name);dialog.dismiss();showPanel()}},LinearLayout.LayoutParams(0,dp(40),1f))
        manageRow.addView(textButton("查看历史",false){showHistory(currentSessionId)},LinearLayout.LayoutParams(0,dp(40),1f))
        content.addView(manageRow)
        content.addView(textButton("节日祝福 · 日常问候 · 客户邀约",true){showGreetingBoard(currentSessionId)},LinearLayout.LayoutParams(-1,dp(44)).apply{topMargin=dp(6)})
        content.addView(sectionTitle("关键词引导").apply{setPadding(0,dp(13),0,dp(7))})
        val keywordRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        presets[category].orEmpty().forEach{word->
            val chip=TextView(this).apply{text=word;textSize=14f;gravity=Gravity.CENTER;setPadding(dp(13),dp(9),dp(13),dp(9))}
            fun paint(){styleChoice(chip,selected.contains(word))};paint();chip.setOnClickListener{if(!selected.add(word))selected.remove(word);paint()}
            keywordRow.addView(chip,LinearLayout.LayoutParams(-2,-2).apply{marginEnd=dp(8)})
        }
        content.addView(HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;addView(keywordRow)})
        val extra=EditText(this).apply{hint="补充要求（可选）";setText(session.optString("extra"));textSize=15f;minLines=2;maxLines=3;setPadding(dp(14),dp(10),dp(14),dp(10));background=outlined()}
        content.addView(extra,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(10)})
        val count=session.optJSONArray("history")?.length()?:0
        content.addView(TextView(this).apply{text="${session.optString("name")} · 已保存 $count 条历史";textSize=13f;setTextColor(muted);setPadding(0,dp(10),0,0)})
        fun saveGuide(){SessionStore.updateGuide(this,currentSessionId,selected.joinToString("、"),extra.text.toString().trim())}
        fun generate(message:String,status:TextView){
            if(message.isBlank()){status.text="请先在微信复制一条消息";return}
            saveGuide();SessionStore.append(this,currentSessionId,"对方",message)
            val now=SessionStore.find(this,currentSessionId)?:session;val history=SessionStore.historyText(now)
            status.text="AI 正在结合历史生成…"
            Thread{val reply=AiApi.generate(this,history,category,selected.joinToString("、"),extra.text.toString().trim()).getOrElse{localDraft(category)+"\n\n联网失败：${it.message}"};Handler(Looper.getMainLooper()).post{showReply(reply)}}.start()
        }
        val status=TextView(this).apply{textSize=13f;setTextColor(muted);gravity=Gravity.CENTER;setPadding(0,dp(8),0,0)}
        content.addView(primaryButton("粘贴剪贴板并生成 3 条回复"){
            val clip=(getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).primaryClip
            generate(clip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty().trim(),status)
        },LinearLayout.LayoutParams(-1,dp(52)).apply{topMargin=dp(14)})
        val manual=EditText(this).apply{hint="手动输入（备用，点击下方生成）";textSize=14f;minLines=2;maxLines=4;setPadding(dp(13),dp(10),dp(13),dp(10));background=outlined()}
        content.addView(manual,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(10)})
        content.addView(textButton("根据手动输入生成",true){generate(manual.text.toString().trim(),status)},LinearLayout.LayoutParams(-1,dp(44)).apply{topMargin=dp(6)})
        content.addView(status)
        showOverlayDialog(dialog,ScrollView(this).apply{addView(content)});activeDialog=dialog
    }

    private fun showReply(raw:String){
        activeDialog?.dismiss();val session=SessionStore.find(this,currentSessionId)
        val category=session?.optString("category")?:"客户";val name=session?.optString("name")?:category
        val dialog=Dialog(this);val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(16),dp(18),dp(20));background=rounded(surface,24f)}
        val head=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        head.addView(TextView(this).apply{text="$name · 3条候选回复";textSize=20f;setTextColor(ink);typeface=Typeface.DEFAULT_BOLD},LinearLayout.LayoutParams(0,-2,1f));head.addView(textButton("关闭",false){dialog.dismiss()});box.addView(head)
        val replies=parseReplies(raw)
        replies.forEachIndexed{index,reply->
            val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(15),dp(13),dp(15),dp(12));background=rounded(Color.WHITE,18f);elevation=dp(2).toFloat()}
            card.addView(TextView(this).apply{text="方案 ${index+1}";textSize=12f;setTextColor(main);typeface=Typeface.DEFAULT_BOLD})
            card.addView(TextView(this).apply{text=reply;textSize=16f;setTextColor(ink);setPadding(0,dp(7),0,dp(9));setTextIsSelectable(true)})
            card.addView(textButton("复制并记住这条回复",true){copyAndRemember(reply)},LinearLayout.LayoutParams(-1,dp(42)))
            box.addView(card,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(10)})
        }
        box.addView(sectionTitle("快捷微调").apply{setPadding(0,dp(16),0,dp(8))});val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        listOf("更简短","更温柔","更坚定","换种说法").forEach{directive->row.addView(textButton(directive,false){Thread{val next=AiApi.refine(this,replies.joinToString("|||"),category,directive).getOrElse{"生成失败：${it.message}"};Handler(Looper.getMainLooper()).post{showReply(next)}}.start()},LinearLayout.LayoutParams(0,dp(42),1f).apply{marginEnd=dp(5)})}
        box.addView(row);box.addView(primaryButton("返回当前联系人") {dialog.dismiss();showPanel()},LinearLayout.LayoutParams(-1,dp(48)).apply{topMargin=dp(14)})
        showOverlayDialog(dialog,ScrollView(this).apply{addView(box)});activeDialog=dialog
    }

    private fun showHistory(id:String){
        val session=SessionStore.find(this,id)?:return;val dialog=Dialog(this);val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(16),dp(18),dp(20));background=rounded(surface,24f)}
        val head=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};head.addView(TextView(this).apply{text="${session.optString("name")} · 历史记录";textSize=20f;setTextColor(ink);typeface=Typeface.DEFAULT_BOLD},LinearLayout.LayoutParams(0,-2,1f));head.addView(textButton("关闭",false){dialog.dismiss()});box.addView(head)
        val history=session.optJSONArray("history")
        if(history==null||history.length()==0)box.addView(TextView(this).apply{text="暂无历史记录";setTextColor(muted);setPadding(0,dp(24),0,dp(24));gravity=Gravity.CENTER})
        else for(i in 0 until history.length()){
            val turn=history.optJSONObject(i)?:continue;val row=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(13),dp(11),dp(13),dp(8));background=rounded(Color.WHITE,14f)}
            row.addView(TextView(this).apply{text="${turn.optString("role")}：${turn.optString("text")}";textSize=15f;setTextColor(ink)})
            row.addView(textButton("删除这条记录",false){confirm("删除这条记录？"){SessionStore.removeTurn(this,id,i);dialog.dismiss();showHistory(id)}})
            box.addView(row,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8)})
        }
        box.addView(textButton("清空全部历史",false){confirm("清空当前联系人的全部历史？"){SessionStore.clear(this,id);dialog.dismiss();showHistory(id)}},LinearLayout.LayoutParams(-1,dp(44)).apply{topMargin=dp(12)})
        showOverlayDialog(dialog,ScrollView(this).apply{addView(box)});activeDialog=dialog
    }

    private fun showGreetingBoard(id:String){
        val session=SessionStore.find(this,id)?:return
        val name=session.optString("name");val category=session.optString("category")
        val dialog=Dialog(this);val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(16),dp(18),dp(20));background=rounded(surface,24f)}
        val head=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        head.addView(TextView(this).apply{text="$name · 问候与邀约";textSize=20f;setTextColor(ink);typeface=Typeface.DEFAULT_BOLD},LinearLayout.LayoutParams(0,-2,1f));head.addView(textButton("关闭",false){dialog.dismiss()});box.addView(head)
        box.addView(TextView(this).apply{text="点击场景，AI 会结合联系人和历史生成三条话术";textSize=13f;setTextColor(muted);setPadding(0,dp(4),0,dp(12))})
        val groups=listOf(
            "中国节日祝福" to listOf("元旦","春节","元宵节","清明节","劳动节","端午节","七夕","中秋节","国庆节","重阳节","冬至","腊八节","小年"),
            "日常问候" to listOf("早安问候","周末愉快","天气转凉","久未联系","感谢支持","生日祝福"),
            "客户邀约" to listOf("邀约见面","到店体验","吃饭交流","查看方案","参加活动","节后拜访")
        )
        val status=TextView(this).apply{textSize=13f;setTextColor(muted);gravity=Gravity.CENTER;setPadding(0,dp(10),0,0)}
        groups.forEach{(group,items)->
            box.addView(sectionTitle(group).apply{setPadding(0,dp(10),0,dp(7))})
            val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
            items.forEach{scene->row.addView(textButton(scene,true){
                status.text="正在为 $name 生成${scene}话术…"
                val history=SessionStore.historyText(SessionStore.find(this,id)?:session)
                val prompt="联系人：$name\n已有沟通背景：${history.ifBlank{"暂无历史"}}\n请生成适合发送的${scene}话术。"
                Thread{val reply=AiApi.generate(this,prompt,category,"$group、$scene",session.optString("extra")).getOrElse{"生成失败：${it.message}"};Handler(Looper.getMainLooper()).post{showReply(reply)}}.start()
            },LinearLayout.LayoutParams(-2,dp(40)).apply{marginEnd=dp(7)})}
            box.addView(HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;addView(row)})
        }
        box.addView(status);box.addView(textButton("返回联系人",false){dialog.dismiss();showPanel()},LinearLayout.LayoutParams(-1,dp(44)).apply{topMargin=dp(10)})
        showOverlayDialog(dialog,ScrollView(this).apply{addView(box)});activeDialog=dialog
    }

    private fun promptName(title:String,initial:String,onSave:(String)->Unit){
        val input=EditText(this).apply{setText(initial);selectAll();setPadding(dp(12),dp(10),dp(12),dp(10))}
        val dialog=AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("取消",null).setPositiveButton("保存",null).create();dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{val name=input.text.toString().trim();if(name.isNotBlank()){dialog.dismiss();onSave(name)}}
    }
    private fun confirm(message:String,onYes:()->Unit){val d=AlertDialog.Builder(this).setMessage(message).setNegativeButton("取消",null).setPositiveButton("确认删除"){_,_->onYes()}.create();d.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);d.show()}
    private fun copyAndRemember(text:String){(getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText("AI回复",text));SessionStore.append(this,currentSessionId,"我",text);Toast.makeText(this,"已复制并记入历史",Toast.LENGTH_SHORT).show()}
    private fun parseReplies(raw:String):List<String>{val clean=raw.trim();val parts=clean.split("|||").map{it.trim().replace(Regex("^[1-3][.、：:]\\s*"),"")}.filter{it.isNotBlank()};if(parts.size>=2)return parts.take(3);val n=clean.split(Regex("(?m)^\\s*[1-3][.、：:]\\s*")).map{it.trim()}.filter{it.isNotBlank()};return if(n.size>=2)n.take(3)else listOf(clean)}
    private fun showOverlayDialog(dialog:Dialog,view:View){dialog.setContentView(view);dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);dialog.setOnDismissListener{if(activeDialog===dialog)activeDialog=null};dialog.show();dialog.window?.setBackgroundDrawableResource(android.R.color.transparent);dialog.window?.setLayout((resources.displayMetrics.widthPixels*.94).toInt(),(resources.displayMetrics.heightPixels*.86).toInt())}
    private fun sectionTitle(t:String)=TextView(this).apply{text=t;textSize=14f;setTextColor(ink);typeface=Typeface.DEFAULT_BOLD}
    private fun primaryButton(t:String,onClick:()->Unit)=Button(this).apply{text=t;textSize=16f;setTextColor(Color.WHITE);isAllCaps=false;background=rounded(main,16f);setOnClickListener{onClick()}}
    private fun textButton(t:String,accent:Boolean,onClick:()->Unit)=TextView(this).apply{text=t;textSize=14f;gravity=Gravity.CENTER;setTextColor(if(accent)main else muted);setPadding(dp(10),dp(7),dp(10),dp(7));background=rounded(if(accent)Color.parseColor("#EAF1FF")else Color.TRANSPARENT,12f);setOnClickListener{onClick()}}
    private fun styleChoice(v:TextView,on:Boolean){v.setTextColor(if(on)Color.WHITE else ink);v.background=rounded(if(on)main else Color.parseColor("#E9EEF6"),14f)}
    private fun rounded(color:Int,radius:Float)=GradientDrawable().apply{shape=GradientDrawable.RECTANGLE;setColor(color);cornerRadius=radius}
    private fun outlined()=GradientDrawable().apply{setColor(Color.WHITE);cornerRadius=16f;setStroke(dp(1),Color.parseColor("#D0D5DD"))}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun localDraft(c:String)=when(c){"朋友"->"收到，我先看看怎么安排，晚点给你准信。|||行，我了解了，等我确认一下再跟你说。|||明白，我先处理手头的事，很快回复你。";"老婆"->"我知道你在意这个，是我考虑得不够周到，我会认真处理。|||别生气，我先把事情处理好，晚点认真跟你说。|||我明白你的感受，这件事我会放在心上。";else->"我理解你的意思，这件事我也很重视，我先核实清楚后尽快回复你。|||收到，我马上确认具体情况，确认后第一时间给你答复。|||这个问题我们会积极处理，具体方案核实后回复。"}
    private fun createChannel(){if(Build.VERSION.SDK_INT>=26)(getSystemService(NOTIFICATION_SERVICE)as NotificationManager).createNotificationChannel(NotificationChannel("assistant","AI悬浮助手",NotificationManager.IMPORTANCE_LOW))}
    override fun onDestroy(){activeDialog?.dismiss();activeDialog=null;if(::bubble.isInitialized)try{wm.removeView(bubble)}catch(_:Exception){};super.onDestroy()}
}
