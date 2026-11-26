package com.neb.ians.ui.components.m3x

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** One piece of a drawing: a path in the art's own units, painted with tone [tone] of the palette. */
class M3xOp(val path: Path, val tone: Int, val alpha: Float, strokeWidth: Float, round: Boolean) {
    val style: DrawStyle = if (strokeWidth > 0f) Stroke(strokeWidth, cap = if (round) StrokeCap.Round else StrokeCap.Butt, join = StrokeJoin.Round) else Fill
}

class M3xPiece(val w: Float, val h: Float, val ops: List<M3xOp>)

/** Tonal palettes of the M3X art library v7 (concepts/art-v7/v7-core.js). Tone order: page, soft, mid, strong, ink, accent, mist, deep. */
object M3xPalette {
    // name, hue, accent hue, saturation scale
    private val FAM = listOf(
        listOf("azure", 218, 44, 1.0), listOf("sky", 200, 18, 1.0), listOf("aqua", 180, 8, 1.0), listOf("teal", 164, 36, 1.0),
        listOf("mint", 142, 340, 1.0), listOf("moss", 98, 210, 1.0), listOf("amber", 42, 215, 1.0), listOf("orange", 22, 205, 1.0),
        listOf("coral", 6, 190, 1.0), listOf("rose", 346, 160, 1.0), listOf("periwinkle", 230, 48, 1.0), listOf("slate", 214, 38, 0.35),
        listOf("sage", 128, 22, 0.55), listOf("clay", 14, 196, 0.6), listOf("ocean", 205, 28, 1.0), listOf("citrine", 56, 214, 0.9),
        listOf("cherry", 352, 172, 0.95), listOf("lagoon", 188, 14, 0.9),
    )
    private val cache = HashMap<String, Array<Color>>()

    private fun rd(v: Double): Float = floor(v + 0.5).toFloat()
    private fun hsl(h: Number, s: Double, l: Double) = Color.hsl(rd(h.toDouble()).coerceIn(0f, 360f), (rd(s) / 100f).coerceIn(0f, 1f), (rd(l) / 100f).coerceIn(0f, 1f))

    @Synchronized
    fun tones(name: String, dark: Boolean): Array<Color> = cache.getOrPut(name + if (dark) "d" else "l") {
        val f = FAM.firstOrNull { it[0] == name } ?: FAM[0]
        val h = f[1] as Int; val ha = f[2] as Int; val k = f[3] as Double
        if (dark) arrayOf(hsl(h, 46 * k, 9.0), hsl(h, 50 * k, 17.0), hsl(h, 56 * k, 30.0), hsl(h, 70 * k, 62.0), hsl(h, 74 * k, 85.0), hsl(ha, 80.0, 62.0), hsl(h, 44 * k, 13.0), hsl(h, 56 * k, 42.0))
        else arrayOf(hsl(h, 82 * k, 92.0), hsl(h, 76 * k, 82.0), hsl(h, 72 * k, 66.0), hsl(h, 88 * k, 40.0), hsl(h, 92 * k, 15.0), hsl(ha, 86.0, 62.0), hsl(h, 80 * k, 96.0), hsl(h, 78 * k, 52.0))
    }
}

/** Seeded builder: same seed key in, same drawing out, on the site (js/m3x-art.js) and here. Calls are made in the same order as the JS layouts. */
class M3xBuilder(key: String, val w: Double, val h: Double) {
    val u = min(w, h)
    val ops = ArrayList<M3xOp>()
    private var a: Int = run { var x = 2166136261L.toInt(); for (ch in key) x = (x xor ch.code) * 16777619; x }

    fun r(): Double {
        a += 0x6D2B79F5
        var t = (a xor (a ushr 15)) * (1 or a)
        t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL) / 4294967296.0
    }
    fun rr(lo: Double, hi: Double) = lo + (hi - lo) * r()
    fun <T> pick(l: List<T>): T = l[floor(r() * l.size).toInt()]
    fun shapeKind(): String = pick(SHAPES)
    fun fl(v: Double) = floor(v).toInt()

    private fun add(p: Path, tone: Int, alpha: Double = 1.0, stroke: Double = 0.0, round: Boolean = false) { ops.add(M3xOp(p, tone, alpha.toFloat(), stroke.toFloat(), round)) }
    private fun turn(p: Path, deg: Double, cx: Double, cy: Double): Path {
        if (deg == 0.0) return p
        val m = Matrix(); m.translate(cx.toFloat(), cy.toFloat()); m.rotateZ(deg.toFloat()); m.translate(-cx.toFloat(), -cy.toFloat()); p.transform(m); return p
    }

    fun sh(kind: String, cx: Double, cy: Double, rad: Double, rot: Double, tone: Int, alpha: Double = 1.0) = add(shapePath(kind, cx, cy, rad, rot), tone, alpha)
    fun ci(cx: Double, cy: Double, rad: Double, tone: Int, alpha: Double = 1.0) =
        add(Path().apply { addOval(Rect((cx - rad).toFloat(), (cy - rad).toFloat(), (cx + rad).toFloat(), (cy + rad).toFloat())) }, tone, alpha)
    fun rc(x: Double, y: Double, rw: Double, rh: Double, rx: Double, tone: Int, deg: Double = 0.0, pcx: Double = 0.0, pcy: Double = 0.0) {
        val c = max(0.0, min(rx, min(rw, rh) / 2)).toFloat()
        val p = Path().apply { addRoundRect(RoundRect(x.toFloat(), y.toFloat(), (x + rw).toFloat(), (y + rh).toFloat(), c, c)) }
        add(turn(p, deg, pcx, pcy), tone)
    }
    /** Quarter disc in one corner of the cell: q 0 top left, 1 top right, 2 bottom right, 3 bottom left (taken modulo 4). */
    fun quarter(x: Double, y: Double, s: Double, q: Int, tone: Int) {
        val k = q % 4
        val cx = if (k == 1 || k == 2) x + s else x
        val cy = if (k >= 2) y + s else y
        val a0 = k * 90.0
        add(wedgePath(cx, cy, s, a0, 90.0), tone)
    }
    fun arch(x: Double, y: Double, aw: Double, ah: Double, tone: Int) {
        val rad = aw / 2
        val p = Path().apply {
            moveTo(x.toFloat(), (y + ah).toFloat()); lineTo(x.toFloat(), (y + rad).toFloat())
            arcTo(Rect((x).toFloat(), y.toFloat(), (x + aw).toFloat(), (y + aw).toFloat()), 180f, 180f, false)
            lineTo((x + aw).toFloat(), (y + ah).toFloat()); close()
        }
        add(p, tone)
    }
    /** Pie wedge from the centre, angles in degrees, sweeping clockwise on screen. */
    fun wedge(cx: Double, cy: Double, rad: Double, a0Deg: Double, sweepDeg: Double, tone: Int, turnDeg: Double = 0.0) =
        add(turn(wedgePath(cx, cy, rad, a0Deg, sweepDeg), turnDeg, cx, cy), tone)
    fun poly(pts: List<DoubleArray>, tone: Int) {
        val p = Path(); pts.forEachIndexed { i, q -> if (i == 0) p.moveTo(q[0].toFloat(), q[1].toFloat()) else p.lineTo(q[0].toFloat(), q[1].toFloat()) }; p.close(); add(p, tone)
    }
    fun ring(cx: Double, cy: Double, rad: Double, sw: Double, tone: Int, alpha: Double) =
        add(Path().apply { addOval(Rect((cx - rad).toFloat(), (cy - rad).toFloat(), (cx + rad).toFloat(), (cy + rad).toFloat())) }, tone, alpha, sw)
    fun arc(cx: Double, cy: Double, rad: Double, a0: Double, a1: Double, sw: Double, tone: Int) {
        val p = Path().apply { arcTo(Rect((cx - rad).toFloat(), (cy - rad).toFloat(), (cx + rad).toFloat(), (cy + rad).toFloat()), (a0 * 180 / PI).toFloat(), ((a1 - a0) * 180 / PI).toFloat(), true) }
        add(p, tone, 1.0, sw, true)
    }
    fun curve(pts: List<DoubleArray>, sw: Double, tone: Int) {
        val p = Path(); p.moveTo(pts[0][0].toFloat(), pts[0][1].toFloat())
        for (i in 1 until pts.size - 1) p.quadraticBezierTo(pts[i][0].toFloat(), pts[i][1].toFloat(), ((pts[i][0] + pts[i + 1][0]) / 2).toFloat(), ((pts[i][1] + pts[i + 1][1]) / 2).toFloat())
        p.lineTo(pts.last()[0].toFloat(), pts.last()[1].toFloat()); add(p, tone, 1.0, sw, true)
    }
    fun path(tone: Int, build: Path.() -> Unit) = add(Path().apply(build), tone)

    private fun wedgePath(cx: Double, cy: Double, rad: Double, a0Deg: Double, sweepDeg: Double) = Path().apply {
        moveTo(cx.toFloat(), cy.toFloat())
        lineTo((cx + rad * cos(a0Deg * PI / 180)).toFloat(), (cy + rad * sin(a0Deg * PI / 180)).toFloat())
        arcTo(Rect((cx - rad).toFloat(), (cy - rad).toFloat(), (cx + rad).toFloat(), (cy + rad).toFloat()), a0Deg.toFloat(), sweepDeg.toFloat(), false)
        close()
    }

    companion object {
        val SHAPES = listOf("cookie", "sunny", "flower", "clover", "burst", "squircle", "pebble")
        private fun rho(kind: String, t: Double): Double = when (kind) {
            "cookie" -> 1 + 0.07 * cos(9 * t)
            "sunny" -> 1 + 0.13 * cos(8 * t)
            "flower" -> 0.74 + 0.26 * cos(6 * t)
            "clover" -> 0.66 + 0.34 * abs(cos(2 * t))
            "burst" -> 0.8 + 0.2 * abs(cos(6 * t)).pow(0.6)
            "squircle" -> (abs(cos(t)).pow(4) + abs(sin(t)).pow(4)).pow(-0.25)
            "pebble" -> 1 + 0.1 * cos(2 * t + 1) + 0.06 * cos(3 * t)
            else -> 1.0
        }
        private fun lobes(kind: String) = when (kind) { "cookie" -> 9; "sunny" -> 8; "flower" -> 6; "burst" -> 12; "squircle" -> 5; else -> 4 }

        /** Polar silhouette sampled and smoothed through midpoints, as N.shapeD in art-core.js. */
        fun shapePath(kind: String, cx: Double, cy: Double, rad: Double, rot: Double): Path {
            val n = max(32, lobes(kind) * 6)
            val xs = DoubleArray(n); val ys = DoubleArray(n)
            for (i in 0 until n) { val t = i.toDouble() / n * PI * 2; val d = rho(kind, t) * rad; xs[i] = cx + d * cos(t + rot); ys[i] = cy + d * sin(t + rot) }
            val p = Path()
            p.moveTo(((xs[0] + xs[1]) / 2).toFloat(), ((ys[0] + ys[1]) / 2).toFloat())
            for (i in 1..n) { val a = i % n; val b = (i + 1) % n; p.quadraticBezierTo(xs[a].toFloat(), ys[a].toFloat(), ((xs[a] + xs[b]) / 2).toFloat(), ((ys[a] + ys[b]) / 2).toFloat()) }
            p.close(); return p
        }
    }
}
