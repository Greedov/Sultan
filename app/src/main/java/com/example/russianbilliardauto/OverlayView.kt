package com.example.russianbilliardauto

import android.graphics.*
import android.view.View

class OverlayView(ctx: android.content.Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var plan: ShotPlan? = null
    var table: TableRect? = null
    var ballCount: Int = 0
    var locked: Boolean = false
    var statusText: String? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val pl = plan

        table?.let { t ->
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = Color.argb(90, 0, 255, 120)
            c.drawRect(t.left, t.top, t.right, t.bottom, paint)
        }

        if (pl != null) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5.5f
            paint.color = Color.WHITE
            pl.cuePath.forEach { seg ->
                c.drawLine(seg.a.x, seg.a.y, seg.b.x, seg.b.y, paint)
            }

            paint.strokeWidth = 4f
            paint.color = Color.YELLOW
            pl.targetPath.forEach { seg ->
                c.drawLine(seg.a.x, seg.a.y, seg.b.x, seg.b.y, paint)
            }

            paint.color = Color.CYAN
            paint.strokeWidth = 3f
            c.drawLine(pl.cue.x, pl.cue.y, pl.ghost.x, pl.ghost.y, paint)

            paint.style = Paint.Style.FILL
            paint.color = Color.argb(110, 200, 220, 255)
            c.drawCircle(pl.ghost.x, pl.ghost.y, pl.cue.r * 1.05f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.5f
            paint.color = Color.argb(200, 180, 220, 255)
            c.drawCircle(pl.ghost.x, pl.ghost.y, pl.cue.r * 1.05f, paint)

            paint.style = Paint.Style.FILL
            paint.color = Color.argb(160, 255, 255, 255)
            c.drawCircle(pl.cue.x, pl.cue.y, pl.cue.r * 1.3f, paint)
            paint.style = Paint.Style.STROKE
            paint.color = Color.WHITE
            paint.strokeWidth = 3f
            c.drawCircle(pl.cue.x, pl.cue.y, pl.cue.r * 1.3f, paint)

            paint.style = Paint.Style.STROKE
            paint.color = Color.RED
            paint.strokeWidth = 3.5f
            c.drawCircle(pl.target.x, pl.target.y, pl.target.r * 1.45f, paint)
        }

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(180, 0, 0, 0)
        c.drawRoundRect(24f, 24f, 460f, 118f, 16f, 16f, paint)
        paint.color = Color.WHITE
        paint.textSize = 26f
        paint.isFakeBoldText = true
        val title = when {
            statusText != null -> statusText!!
            locked -> "LOCK"
            pl != null -> "Svoyak -> tsel"
            else -> "Poisk..."
        }
        c.drawText(title, 40f, 60f, paint)
        paint.textSize = 22f
        paint.isFakeBoldText = false
        val sub = if (pl != null) {
            "Balls: $ballCount  score ${"%.0f".format(pl.score)}"
        } else {
            "Balls: $ballCount"
        }
        c.drawText(sub, 40f, 96f, paint)
    }
}
