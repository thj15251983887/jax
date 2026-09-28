package com.tang.wechatassistant

import android.app.*
import android.content.*
import android.media.projection.MediaProjectionManager
import android.os.Bundle

class CaptureActivity : Activity() {
    private val req = 501
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mgr.createScreenCaptureIntent(), req)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == req && resultCode == RESULT_OK && data != null) {
            val i = Intent(this, CaptureService::class.java).apply {
                putExtra("resultCode", resultCode); putExtra("data", data)
            }
            startForegroundService(i)
        } else sendBroadcast(Intent("com.tang.wechatassistant.CAPTURE_FAILED").setPackage(packageName))
        finish()
    }
}
