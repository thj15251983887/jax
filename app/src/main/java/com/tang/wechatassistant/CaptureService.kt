package com.tang.wechatassistant

import android.app.*
import android.content.*
import android.graphics.*
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjectionManager
import android.os.*
import androidx.core.app.NotificationCompat
import java.io.ByteArrayOutputStream

class CaptureService : Service() {
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel(); startForeground(22, NotificationCompat.Builder(this,"capture").setSmallIcon(android.R.drawable.ic_menu_camera).setContentTitle("正在读取当前屏幕").setContentText("仅本次，用于生成回复建议").build())
        val code = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION") val data = intent?.getParcelableExtra<Intent>("data")
        if (data == null) { stopSelf(); return START_NOT_STICKY }
        val dm = resources.displayMetrics; val w=dm.widthPixels; val h=dm.heightPixels; val density=dm.densityDpi
        val reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2)
        val mgr=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection=mgr.getMediaProjection(code,data)
        projection.registerCallback(object: android.media.projection.MediaProjection.Callback(){ override fun onStop(){} }, Handler(Looper.getMainLooper()))
        val vd=projection.createVirtualDisplay("one-shot",w,h,density,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.surface,null,null)
        reader.setOnImageAvailableListener({ r ->
            val image=r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val plane=image.planes[0]; val buf=plane.buffer; val pixelStride=plane.pixelStride; val rowStride=plane.rowStride
                val rowPadding=rowStride-pixelStride*w
                val tmp=Bitmap.createBitmap(w+rowPadding/pixelStride,h,Bitmap.Config.ARGB_8888); tmp.copyPixelsFromBuffer(buf)
                val bmp=Bitmap.createBitmap(tmp,0,0,w,h); tmp.recycle()
                val out=ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.JPEG,82,out); bmp.recycle()
                val b64=android.util.Base64.encodeToString(out.toByteArray(),android.util.Base64.NO_WRAP)
                Thread {
                    val prefs=getSharedPreferences("settings",MODE_PRIVATE)
                    val relation=prefs.getString("relation","客户")?:"客户"
                    val keywords=prefs.getString("guideKeywords","")?:""
                    val extra=prefs.getString("guideExtra","")?:""
                    val reply=AiApi.generateFromImage(this@CaptureService,b64,relation,keywords,extra).getOrElse { "识别失败：${it.message}" }
                    sendBroadcast(Intent("com.tang.wechatassistant.CAPTURE_REPLY").setPackage(packageName).putExtra("reply",reply))
                    vd.release(); projection.stop(); reader.close(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
                }.start()
            } finally { image.close() }
        },Handler(Looper.getMainLooper()))
        return START_NOT_STICKY
    }
    private fun createChannel(){ if(Build.VERSION.SDK_INT>=26){ val nm=getSystemService(NOTIFICATION_SERVICE) as NotificationManager; nm.createNotificationChannel(NotificationChannel("capture","屏幕读取",NotificationManager.IMPORTANCE_LOW)) } }
}
