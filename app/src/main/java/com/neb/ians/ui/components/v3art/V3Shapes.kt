package com.neb.ians.ui.components.v3art

import com.neb.ians.ui.components.v3art.V3.f
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Shape and SVG-string helpers of v3-core.js. Every function returns an SVG element string, as in the JS. */
internal object A {
    private class Pol(val n: Int, val r: (Double) -> Double)

    private val POL: Map<String, Pol> = linkedMapOf(
        "cookie" to Pol(36) { t -> 1 + 0.07 * cos(9 * t) },
        "cookie12" to Pol(48) { t -> 1 + 0.05 * cos(12 * t) },
        "sunny" to Pol(32) { t -> 1 + 0.13 * cos(8 * t) },
        "burst" to Pol(48) { t -> 0.82 + 0.18 * abs(cos(6 * t)).pow(0.6) },
        "flower" to Pol(36) { t -> 0.76 + 0.24 * cos(6 * t) },
        "clover" to Pol(24) { t -> 0.68 + 0.32 * abs(cos(2 * t)) },
        "puffy" to Pol(30) { t -> 0.8 + 0.2 * cos(5 * t) + 0.03 * cos(10 * t) },
        "pebble" to Pol(20) { t -> 1 + 0.1 * cos(2 * t - 0.6) + 0.05 * cos(3 * t + 0.4) },
        "squircle" to Pol(24) { t -> (abs(cos(t)).pow(4) + abs(sin(t)).pow(4)).pow(-0.25) },
        "circle" to Pol(12) { _ -> 1.0 },
        "gem" to Pol(24) { t -> (abs(cos(t)).pow(1.6) + abs(sin(t)).pow(1.6)).pow(-0.625) },
        "lotus" to Pol(42) { t -> 0.78 + 0.22 * cos(7 * t) + 0.04 * cos(14 * t) }
    )
    val shapeNames: List<String> = POL.keys.toList()

    fun smooth(p: List<DoubleArray>, closed: Boolean): String {
        val n = p.size
        val sb = StringBuilder("M").append(f(p[0][0])).append(' ').append(f(p[0][1]))
        val last = if (closed) n else n - 1
        for (i in 0 until last) {
            var a = p[(i - 1 + n) % n]
            val b = p[i]
            val c = p[(i + 1) % n]
            var e = p[(i + 2) % n]
            if (!closed) { a = p[max(0, i - 1)]; e = p[min(n - 1, i + 2)] }
            sb.append('C').append(f(b[0] + (c[0] - a[0]) / 6)).append(' ').append(f(b[1] + (c[1] - a[1]) / 6)).append(' ')
                .append(f(c[0] - (e[0] - b[0]) / 6)).append(' ').append(f(c[1] - (e[1] - b[1]) / 6)).append(' ')
                .append(f(c[0])).append(' ').append(f(c[1]))
        }
        return sb.append(if (closed) "Z" else "").toString()
    }

    fun shapeD(k: String, cx: Double, cy: Double, r: Double, rot: Double): String {
        val s = POL[k] ?: POL.getValue("cookie")
        val n = max(10, V3.jsRound(s.n * min(1.0, 0.4 + r / 80)).toInt())
        val p = ArrayList<DoubleArray>(n)
        for (i in 0 until n) {
            val t = i.toDouble() / n * PI * 2
            val q = s.r(t) * r
            p.add(doubleArrayOf(cx + q * cos(t + rot), cy + q * sin(t + rot)))
        }
        return smooth(p, true)
    }

    fun rpoly(pts: List<DoubleArray>, rad: Double): String {
        val n = pts.size
        val sb = StringBuilder()
        for (i in 0 until n) {
            val a = pts[(i + n - 1) % n]
            val b = pts[i]
            val c = pts[(i + 1) % n]
            val l1 = hypot(a[0] - b[0], a[1] - b[1])
            val l2 = hypot(c[0] - b[0], c[1] - b[1])
            val r = min(rad, min(l1 / 2.2, l2 / 2.2))
            val px = b[0] + (a[0] - b[0]) / l1 * r
            val py = b[1] + (a[1] - b[1]) / l1 * r
            val qx = b[0] + (c[0] - b[0]) / l2 * r
            val qy = b[1] + (c[1] - b[1]) / l2 * r
            sb.append(if (i > 0) 'L' else 'M').append(f(px)).append(' ').append(f(py)).append('Q').append(f(b[0])).append(' ').append(f(b[1]))
                .append(' ').append(f(qx)).append(' ').append(f(qy))
        }
        return sb.append('Z').toString()
    }

    fun ngon(n: Int, cx: Double, cy: Double, r: Double, rot: Double, rad: Double? = null): String {
        val p = List(n) { i -> doubleArrayOf(cx + r * cos(rot + i.toDouble() / n * PI * 2), cy + r * sin(rot + i.toDouble() / n * PI * 2)) }
        return rpoly(p, rad ?: (r * 0.22))
    }

    private fun at(x: String?) = if (x.isNullOrEmpty()) "" else " $x"

    fun path(d: String, fill: String, ex: String? = null) = "<path d=\"$d\" fill=\"$fill\"${at(ex)}/>"
    fun shape(k: String, cx: Double, cy: Double, r: Double, rot: Double, fill: String, ex: String? = null) = path(shapeD(k, cx, cy, r, rot), fill, ex)
    fun circle(cx: Double, cy: Double, r: Double, fill: String, ex: String? = null) = "<circle cx=\"${f(cx)}\" cy=\"${f(cy)}\" r=\"${f(r)}\" fill=\"$fill\"${at(ex)}/>"
    fun rect(x: Double, y: Double, w: Double, h: Double, rx: Double, fill: String, ex: String? = null) =
        "<rect x=\"${f(x)}\" y=\"${f(y)}\" width=\"${f(w)}\" height=\"${f(h)}\" rx=\"${f(rx)}\" fill=\"$fill\"${at(ex)}/>"
    fun line(x1: Double, y1: Double, x2: Double, y2: Double, sw: Double, stroke: String, ex: String? = null) =
        "<path d=\"M${f(x1)} ${f(y1)}L${f(x2)} ${f(y2)}\" stroke=\"$stroke\" stroke-width=\"${f(sw)}\" stroke-linecap=\"round\" fill=\"none\"${at(ex)}/>"
    fun stroke(d: String, sw: Double, stroke: String, ex: String? = null) =
        "<path d=\"$d\" stroke=\"$stroke\" stroke-width=\"${f(sw)}\" stroke-linecap=\"round\" stroke-linejoin=\"round\" fill=\"none\"${at(ex)}/>"
    fun poly(pts: List<DoubleArray>, fill: String, ex: String? = null) =
        "<path d=\"M${pts.joinToString("L") { f(it[0]) + " " + f(it[1]) }}Z\" fill=\"$fill\"${at(ex)}/>"
    fun arch(x: Double, y: Double, w: Double, h: Double, fill: String, ex: String? = null): String {
        val r = w / 2
        return path("M${f(x)} ${f(y + h)}V${f(y + r)}A${f(r)} ${f(r)} 0 0 1 ${f(x + w)} ${f(y + r)}V${f(y + h)}Z", fill, ex)
    }
    fun star4(cx: Double, cy: Double, r: Double, fill: String): String {
        val k = r * 0.22
        return path(
            "M${f(cx)} ${f(cy - r)}Q${f(cx + k)} ${f(cy - k)} ${f(cx + r)} ${f(cy)}Q${f(cx + k)} ${f(cy + k)} ${f(cx)} ${f(cy + r)}" +
                "Q${f(cx - k)} ${f(cy + k)} ${f(cx - r)} ${f(cy)}Q${f(cx - k)} ${f(cy - k)} ${f(cx)} ${f(cy - r)}Z", fill
        )
    }
    fun fillTo(pts: List<DoubleArray>, bottom: Double, fill: String): String {
        val d = smooth(pts, false) + "L${f(pts.last()[0])} ${f(bottom)}L${f(pts[0][0])} ${f(bottom)}Z"
        return path(d, fill)
    }
    fun pt(x: Double, y: Double) = doubleArrayOf(x, y)
}
