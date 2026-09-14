package com.example.russianbilliardauto

data class Ball(val x: Float, val y: Float, val r: Float, val brightness: Float = 0f)

data class Vec(val x: Float, val y: Float) {
    operator fun plus(o: Vec) = Vec(x + o.x, y + o.y)
    operator fun minus(o: Vec) = Vec(x - o.x, y - o.y)
    operator fun times(k: Float) = Vec(x * k, y * k)
    fun dot(o: Vec) = x * o.x + y * o.y
    fun len() = kotlin.math.sqrt(x * x + y * y)
    fun norm(): Vec {
        val l = len()
        return if (l < 1e-4f) Vec(0f, 0f) else Vec(x / l, y / l)
    }
}

data class Segment(val a: Vec, val b: Vec)

data class ShotPlan(
    val cue: Ball,
    val target: Ball,
    val ghost: Vec,
    val cuePath: List<Segment>,
    val targetPath: List<Segment>,
    val aimDir: Vec,
    val score: Float,
    val pocketIndex: Int
)

data class TableRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun width() = right - left
    fun height() = bottom - top
    fun contains(x: Float, y: Float, margin: Float = 0f) =
        x >= left + margin && x <= right - margin && y >= top + margin && y <= bottom - margin
}
