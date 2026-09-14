package com.example.russianbilliardauto

import android.app.*
import android.content.Intent
import android.graphics.*
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.WindowManager
import kotlin.math.hypot

class CaptureService : Service() {

    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private var wm: WindowManager? = null
    private var overlay: OverlayView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastProcessMs = 0L
    private val minIntervalMs = 90L

    @Volatile private var paused = false
    @Volatile private var locked = false
    private var lockedPlan: ShotPlan? = null
    private var prevPlan: ShotPlan? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            mainHandler.post { stopSelf() }
        }
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                paused = !paused
                updateNotification()
                if (paused) {
                    mainHandler.post {
                        overlay?.statusText = "PAUSE"
                        overlay?.invalidate()
                    }
                }
                return START_STICKY
            }
            ACTION_LOCK -> {
                locked = !locked
                if (locked) lockedPlan = prevPlan else lockedPlan = null
                updateNotification()
                mainHandler.post {
                    overlay?.locked = locked
                    overlay?.invalidate()
                }
                return START_STICKY
            }
        }

        if (projection != null) return START_STICKY

        val data = getCaptureIntent(intent) ?: return START_NOT_STICKY
        val resultCode = data.getIntExtra("resultCode", Activity.RESULT_CANCELED)
        if (resultCode != Activity.RESULT_OK) return START_NOT_STICKY

        createChannel()
        startForeground(NOTIF_ID, buildNotification())

        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mgr.getMediaProjection(resultCode, data)
        projection?.registerCallback(projectionCallback, mainHandler)

        val dm = resources.displayMetrics
        reader = ImageReader.newInstance(
            dm.widthPixels, dm.heightPixels, PixelFormat.RGBA_8888, 2
        )
        virtualDisplay = projection?.createVirtualDisplay(
            "RBA",
            dm.widthPixels, dm.heightPixels, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface, null, mainHandler
        )

        showOverlay()
        reader?.setOnImageAvailableListener({ r -> processFrame(r) }, mainHandler)
        return START_STICKY
    }

    private fun getCaptureIntent(intent: Intent?): Intent? {
        if (intent == null) return null
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra("data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("data")
        }
    }

    private fun showOverlay() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        overlay = OverlayView(this)
        val type = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        wm?.addView(overlay, lp)
    }

    private fun processFrame(r: ImageReader) {
        val now = SystemClock.uptimeMillis()
        if (now - lastProcessMs < minIntervalMs) {
            r.acquireLatestImage()?.close()
            return
        }
        lastProcessMs = now

        if (paused) {
            r.acquireLatestImage()?.close()
            return
        }

        if (locked && lockedPlan != null) {
            r.acquireLatestImage()?.close()
            mainHandler.post {
                overlay?.plan = lockedPlan
                overlay?.locked = true
                overlay?.statusText = "LOCK"
                overlay?.invalidate()
            }
            return
        }

        val image = r.acquireLatestImage() ?: return
        try {
            val bmp = imageToBitmap(image) ?: return
            val work = if (bmp.width > 1280) {
                val scale = 1280f / bmp.width
                Bitmap.createScaledBitmap(bmp, 1280, (bmp.height * scale).toInt().coerceAtLeast(1), true)
            } else bmp

            val scaleX = bmp.width.toFloat() / work.width
            val scaleY = bmp.height.toFloat() / work.height

            val (rawBalls, rawTable) = Vision.detect(work)
            val balls = rawBalls.map {
                Ball(it.x * scaleX, it.y * scaleY, it.r * scaleX, it.brightness)
            }
            val table = TableRect(
                rawTable.left * scaleX, rawTable.top * scaleY,
                rawTable.right * scaleX, rawTable.bottom * scaleY
            )

            val cue = Vision.pickCue(balls)
            var plan = if (cue != null && balls.size >= 2) {
                Physics.bestPlan(cue, balls, table)
            } else null

            plan = smoothPlan(prevPlan, plan)
            prevPlan = plan

            mainHandler.post {
                overlay?.plan = plan
                overlay?.table = table
                overlay?.ballCount = balls.size
                overlay?.locked = false
                overlay?.statusText = null
                overlay?.invalidate()
            }

            if (work !== bmp) work.recycle()
            bmp.recycle()
        } finally {
            image.close()
        }
    }

    private fun smoothPlan(prev: ShotPlan?, next: ShotPlan?): ShotPlan? {
        if (next == null) return prev
        if (prev == null) return next
        val sameTarget = hypot(prev.target.x - next.target.x, prev.target.y - next.target.y) < prev.target.r * 2.5f
        val samePocket = prev.pocketIndex == next.pocketIndex
        if (sameTarget && samePocket && next.score < prev.score + 180f) return prev
        if (next.score < prev.score + 120f && sameTarget) return prev
        return next
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val plane = image.planes.getOrNull(0) ?: return null
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val width = image.width
        val height = image.height
        val rowPadding = rowStride - pixelStride * width
        return try {
            val bmp = Bitmap.createBitmap(
                width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
            )
            buffer.rewind()
            bmp.copyPixelsFromBuffer(buffer)
            if (rowPadding == 0) bmp
            else {
                val cropped = Bitmap.createBitmap(bmp, 0, 0, width, height)
                bmp.recycle()
                cropped
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(CHANNEL_ID, "Russian Billiard Auto", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
    }

    private fun updateNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        fun pi(action: String, req: Int): PendingIntent {
            val i = Intent(this, CaptureService::class.java).apply { this.action = action }
            return PendingIntent.getService(
                this, req, i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val pauseLabel = if (paused) "Resume" else "Pause"
        val lockLabel = if (locked) "Unlock" else "Lock"
        return builder
            .setContentTitle("Russian Billiard Auto")
            .setContentText("Pause / Lock / Stop")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .addAction(0, pauseLabel, pi(ACTION_PAUSE, 1))
            .addAction(0, lockLabel, pi(ACTION_LOCK, 2))
            .addAction(0, "Stop", pi(ACTION_STOP, 3))
            .build()
    }

    override fun onDestroy() {
        try { wm?.removeView(overlay) } catch (_: Exception) {}
        overlay = null
        reader?.setOnImageAvailableListener(null, null)
        reader?.close()
        reader = null
        virtualDisplay?.release()
        virtualDisplay = null
        try { projection?.unregisterCallback(projectionCallback) } catch (_: Exception) {}
        projection?.stop()
        projection = null
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "rba"
        const val NOTIF_ID = 7
        const val ACTION_STOP = "com.example.russianbilliardauto.STOP"
        const val ACTION_PAUSE = "com.example.russianbilliardauto.PAUSE"
        const val ACTION_LOCK = "com.example.russianbilliardauto.LOCK"
    }
}
