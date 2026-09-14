package com.example.russianbilliardauto

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.*

object Vision {

    fun detect(bitmap: Bitmap): Pair<List<Ball>, TableRect> {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 32 || h < 32) {
            return emptyList<Ball>() to TableRect(w * 0.1f, h * 0.1f, w * 0.9f, h * 0.9f)
        }

        var minX = w
        var minY = h
        var maxX = 0
        var maxY = 0
        var greenCount = 0
        val step = max(2, min(w, h) / 400)
        for (y in 0 until h step step) {
            for (x in 0 until w step step) {
                val c = bitmap.getPixel(x, y)
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                if (g > r * 1.20f && g > b * 1.05f && g > 55) {
                    minX = min(minX, x)
                    maxX = max(maxX, x)
                    minY = min(minY, y)
                    maxY = max(maxY, y)
                    greenCount++
                }
            }
        }
        val table = if (greenCount > 80) {
            val inset = min(w, h) * 0.008f
            TableRect(
                minX.toFloat() + inset,
                minY.toFloat() + inset,
                maxX.toFloat() - inset,
                maxY.toFloat() - inset
            )
        } else {
            TableRect(w * 0.08f, h * 0.08f, w * 0.92f, h * 0.92f)
        }

        val cell = max(3, min(w, h) / 280)
        val gridW = ((table.width() / cell).toInt() + 1).coerceAtLeast(1)
        val gridH = ((table.height() / cell).toInt() + 1).coerceAtLeast(1)
        val hit = BooleanArray(gridW * gridH)
        val brightSum = FloatArray(gridW * gridH)

        for (y in table.top.toInt() until table.bottom.toInt() step cell) {
            for (x in table.left.toInt() until table.right.toInt() step cell) {
                val px = x.coerceIn(0, w - 1)
                val py = y.coerceIn(0, h - 1)
                val c = bitmap.getPixel(px, py)
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                val mx = max(r, max(g, b))
                val mn = min(r, min(g, b))
                if (mx > 140 && (mx - mn) < 90 && (r + g + b) > 380) {
                    val gx = ((x - table.left) / cell).toInt().coerceIn(0, gridW - 1)
                    val gy = ((y - table.top) / cell).toInt().coerceIn(0, gridH - 1)
                    val idx = gy * gridW + gx
                    hit[idx] = true
                    brightSum[idx] += (r + g + b) / 3f
                }
            }
        }

        val seen = BooleanArray(hit.size)
        val candidates = mutableListOf<Ball>()
        val typicalR = max(6f, min(w, h) * 0.0135f)
        val minCells = 2
        val maxCells = max(8, (typicalR * 2.2f / cell).toInt().coerceAtLeast(4))

        for (i in hit.indices) {
            if (!hit[i] || seen[i]) continue
            val q = ArrayDeque<Int>()
            q.add(i)
            seen[i] = true
            var sx = 0f
            var sy = 0f
            var sumBright = 0f
            var cnt = 0
            while (q.isNotEmpty()) {
                val j = q.removeFirst()
                val gx = j % gridW
                val gy = j / gridW
                sx += gx
                sy += gy
                sumBright += brightSum[j]
                cnt++
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = gx + dx
                        val ny = gy + dy
                        if (nx in 0 until gridW && ny in 0 until gridH) {
                            val k = ny * gridW + nx
                            if (hit[k] && !seen[k]) {
                                seen[k] = true
                                q.add(k)
                            }
                        }
                    }
                }
            }
            if (cnt < minCells || cnt > maxCells) continue
            val cx = table.left + (sx / cnt) * cell + cell * 0.5f
            val cy = table.top + (sy / cnt) * cell + cell * 0.5f
            if (!table.contains(cx, cy, typicalR * 0.6f)) continue
            val brightness = sumBright / cnt
            candidates.add(Ball(cx, cy, typicalR, brightness))
        }

        val merged = mutableListOf<Ball>()
        for (b in candidates.sortedByDescending { it.brightness }) {
            if (merged.none { hypot(it.x - b.x, it.y - b.y) < typicalR * 1.35f }) {
                merged.add(b)
            }
        }
        return merged.take(20) to table
    }

    fun pickCue(balls: List<Ball>): Ball? =
        balls.maxByOrNull { it.brightness }
}
