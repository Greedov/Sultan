package com.example.russianbilliardauto

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val REQ_CAPTURE = 7001
    private val REQ_OVERLAY = 7002

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 56, 40, 40)
        }
        val title = TextView(this).apply {
            text = "Russian Billiard Auto"
            textSize = 26f
        }
        val info = TextView(this).apply {
            text = "1. Overlay\n2. START\n3. Open game\n4. Stop in notification"
            textSize = 15f
            setPadding(0, 16, 0, 24)
        }
        val btnStart = Button(this).apply {
            text = "START"
            setOnClickListener { ensureOverlayThenCapture() }
        }
        val btnStop = Button(this).apply {
            text = "STOP"
            setOnClickListener {
                startService(Intent(this@MainActivity, CaptureService::class.java).apply {
                    action = CaptureService.ACTION_STOP
                })
                Toast.makeText(this@MainActivity, "Stopped", Toast.LENGTH_SHORT).show()
            }
        }
        box.addView(title)
        box.addView(info)
        box.addView(btnStart)
        box.addView(btnStop)
        setContentView(box)
    }

    private fun ensureOverlayThenCapture() {
        if (!Settings.canDrawOverlays(this)) {
            startActivityForResult(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                ),
                REQ_OVERLAY
            )
            Toast.makeText(this, "Enable overlay", Toast.LENGTH_LONG).show()
            return
        }
        requestCapture()
    }

    private fun requestCapture() {
        val m = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(m.createScreenCaptureIntent(), REQ_CAPTURE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                requestCapture()
            } else {
                Toast.makeText(this, "Need overlay", Toast.LENGTH_LONG).show()
            }
            return
        }
        if (requestCode == REQ_CAPTURE && resultCode == RESULT_OK && data != null) {
            data.putExtra("resultCode", resultCode)
            val i = Intent(this, CaptureService::class.java).apply {
                putExtra("data", data)
            }
            startForegroundService(i)
            Toast.makeText(this, "Started", Toast.LENGTH_SHORT).show()
        }
    }
}
