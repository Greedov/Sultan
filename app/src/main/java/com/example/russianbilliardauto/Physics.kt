package com.example.russianbilliardauto

import kotlin.math.*

object Physics {

    private fun reflect(v: Vec, n: Vec) = v - n * (2f * v.dot(n))

    private fun path(start: Vec, dir: Vec, table: TableRect, r: Float, maxBounces: Int): List<Segment> {
        val out = mutableListOf<Segment>()
        var p = start
        var d = dir.norm()
        if (d.len() < 1e-4f) return out

        repeat(maxBounces + 1) {
            var t = Float.POSITIVE_INFINITY
            var normal = Vec(0f, 0f)
            val left = table.left + r
            val right = table.right - r
            val top = table.top + r
            val bottom = table.bottom - r

            if (d.x < -1e-6f) {
                val z = (left - p.x) / d.x
                if (z > 1e-3f && z < t) { t = z; normal = Vec(1f, 0f) }
            }
            if (d.x > 1e-6f) {
                val z = (right - p.x) / d.x
                if (z > 1e-3f && z < t) { t = z; normal = Vec(-1f, 0f) }
            }
            if (d.y < -1e-6f) {
                val z = (top - p.y) / d.y
                if (z > 1e-3f && z < t) { t = z; normal = Vec(0f, 1f) }
            }
            if (d.y > 1e-6f) {
                val z = (bottom - p.y) / d.y
                if (z > 1e-3f && z < t) { t = z; normal = Vec(0f, -1f) }
            }

            if (!t.isFinite() || t > 8000f) {
                out.add(Segment(p, p + d * 2500f))
                return@repeat
            }
            val hit = p + d * t
            out.add(Segment(p, hit))
            p = hit
            d = reflect(d, normal).norm()
        }
        return out
    }

    private fun pockets(table: TableRect): List<Vec> {
        val cx = (table.left + table.right) * 0.5f
        val m = min(table.width(), table.height()) * 0.02f
        return listOf(
            Vec(table.left - m, table.top - m),
            Vec(cx, table.top - m),
            Vec(table.right + m, table.top - m),
            Vec(table.left - m, table.bottom + m),
            Vec(cx, table.bottom + m),
            Vec(table.right + m, table.bottom + m)
        )
    }

    fun bestPlan(cue: Ball, balls: List<Ball>, table: TableRect): ShotPlan? {
        val others = balls.filter { hypot(it.x - cue.x, it.y - cue.y) > cue.r * 1.5f }
        if (others.isEmpty()) return null

        val pocketList = pockets(table)
        var best: ShotPlan? = null
        val tableDiag = (table.width() + table.height()).coerceAtLeast(1f)

        for (target in others) {
            for ((pi, pocket) in pocketList.withIndex()) {
                val toPocketVec = pocket - Vec(target.x, target.y)
                val distToPocket = toPocketVec.len()
                val toPocket = toPocketVec.norm()
                if (toPocket.len() < 1e-4f) continue

                val nearPocketPenalty = if (distToPocket < target.r * 2.5f) 600f else 0f
                val ghost = Vec(target.x, target.y) - toPocket * (target.r + cue.r)
                val cueToGhost = ghost - Vec(cue.x, cue.y)
                val dist = cueToGhost.len()
                if (dist < cue.r * 0.5f) continue
                val aimDir = cueToGhost.norm()

                val cueToTarget = (Vec(target.x, target.y) - Vec(cue.x, cue.y)).norm()
                val alignment = aimDir.dot(cueToTarget).coerceIn(-1f, 1f)
                val cut = aimDir.dot(toPocket).coerceIn(-1f, 1f)

                val cutAngleDeg = acos(cut.coerceIn(-1f, 1f)) * 180f / PI.toFloat()
                val sharpCutPenalty = when {
                    cutAngleDeg > 75f -> 500f
                    cutAngleDeg > 60f -> 250f
                    else -> 0f
                }

                val distNorm = dist / tableDiag
                val score = alignment * 1200f - distNorm * 400f + cut * 200f -
                    nearPocketPenalty - sharpCutPenalty

                if (best == null || score > best.score) {
                    val cuePath = path(Vec(cue.x, cue.y), aimDir, table, cue.r, 3)
                    val targetPath = path(Vec(target.x, target.y), toPocket, table, target.r, 3)
                    best = ShotPlan(
                        cue = cue,
                        target = target,
                        ghost = ghost,
                        cuePath = cuePath,
                        targetPath = targetPath,
                        aimDir = aimDir,
                        score = score,
                        pocketIndex = pi
                    )
                }
            }
        }
        return best
    }
}
