package com.neb.ians.ui.components.v3art

import com.neb.ians.ui.components.v3art.V3.f
import kotlin.math.PI
import kotlin.math.ceil

/** What an engine is handed: box, palette, seeded random (v3-make.js). */
internal class V3Ctx(val w: Double, val h: Double, val P: V3.Pal, val r: V3.Rng)

/** Shared pieces of v3-patterns.js. */
internal object V3Pat {
    fun tones(c: V3Ctx) = listOf(c.P.t(0.14), c.P.t(0.34), c.P.t(0.58), c.P.t(0.82), c.P.sp(0.55))
    fun bg(c: V3Ctx, T: List<String>) = A.rect(0.0, 0.0, c.w, c.h, 0.0, if (c.r.chance(0.5)) c.P.sky else T[0])
    fun quarter(x: Double, y: Double, s: Double, q: Int, fill: String): String {
        val a = when (q % 4) {
            0 -> doubleArrayOf(x, y, x + s, y, x, y + s)
            1 -> doubleArrayOf(x + s, y, x + s, y + s, x, y)
            2 -> doubleArrayOf(x + s, y + s, x, y + s, x + s, y)
            else -> doubleArrayOf(x, y + s, x, y, x + s, y + s)
        }
        return A.path("M${f(a[0])} ${f(a[1])}L${f(a[2])} ${f(a[3])}A${f(s)} ${f(s)} 0 0 ${if (q % 2 == 1) 0 else 1} ${f(a[4])} ${f(a[5])}Z", fill)
    }
    fun cells(c: V3Ctx, n: Int, fn: (Double, Double, Double, Int, Int) -> String): String {
        val s = c.h / n
        val cols = ceil(c.w / s).toInt()
        val sb = StringBuilder()
        for (j in 0 until n) for (i in 0 until cols) sb.append(fn(i * s, j * s, s, i, j))
        return sb.toString()
    }
}

internal object V3PatternsA {
    private fun rr(x: Double, y: Double, w: Double, h: Double, rx: Double, fill: String) = A.rect(x, y, w, h, rx, fill)
    private fun cc(x: Double, y: Double, r: Double, fill: String) = A.circle(x, y, r, fill)
    private fun poly(fill: String, vararg xy: Double) = A.poly(List(xy.size / 2) { A.pt(xy[it * 2], xy[it * 2 + 1]) }, fill)

    val all: Map<String, (V3Ctx) -> String> = mapOf(
        "truchet" to { c: V3Ctx ->
            val T = V3Pat.tones(c)
            val r = c.r
            val a = r.pick(listOf(1, 2, 3))
            val b = r.pick(listOf(0, 3, 4))
            V3Pat.bg(c, T) + V3Pat.cells(c, r.int(4, 8)) { x, y, s, _, _ ->
                rr(x, y, s + 0.5, s + 0.5, 0.0, T[a]) + V3Pat.quarter(x, y, s + 0.3, r.int(0, 3), T[b]) +
                    (if (r.chance(0.5)) V3Pat.quarter(x + s, y + s, s * 0.5, r.int(0, 3), T[(b + 1) % 5]) else "")
            }
        },
        "dhaka" to { c: V3Ctx ->
            val T = V3Pat.tones(c)
            val r = c.r
            val w = c.w
            val h = c.h
            val n = r.int(6, 9)
            val b = h / n
            var o = V3Pat.bg(c, T)
            for (i in 0 until n) {
                val y = i * b
                val t = i % 4
                val s = b * 0.9
                val cnt = ceil(w / s).toInt() + 1
                o += rr(0.0, y, w, b + 0.5, 0.0, T[(i * 2 + 1) % 4])
                for (k in 0 until cnt) {
                    val x = k * s
                    val m = x + s / 2
                    o += when (t) {
                        0 -> poly(T[(i + 2) % 5], x, y + b, m, y + b * 0.1, x + s, y + b)
                        1 -> A.path(A.rpoly(listOf(A.pt(m, y + b * 0.08), A.pt(x + s * 0.9, y + b / 2), A.pt(m, y + b * 0.92), A.pt(x + s * 0.1, y + b / 2)), b * 0.12), T[4]) + cc(m, y + b / 2, b * 0.1, T[0])
                        2 -> rr(x + s * 0.15, y + b * 0.2, s * 0.7, b * 0.6, b * 0.12, T[(i + 3) % 5]) + rr(x + s * 0.35, y + b * 0.4, s * 0.3, b * 0.2, b * 0.05, T[0])
                        else -> poly(T[(i + 1) % 5], x, y, m, y + b * 0.9, x + s, y)
                    }
                }
            }
            o
        },
        "lattice" to { c: V3Ctx ->
            val T = V3Pat.tones(c)
            val n = c.r.int(4, 6)
            rr(0.0, 0.0, c.w, c.h, 0.0, T[1]) + V3Pat.cells(c, n) { x, y, s, i, j ->
                val m = s / 2
                val q = (i + j) % 2 == 1
                rr(x + s * 0.06, y + s * 0.06, s * 0.88, s * 0.88, s * 0.2, T[0]) +
                    A.path(A.ngon(4, x + m, y + m, s * 0.36, PI / 4, s * 0.1), if (q) T[3] else T[2]) +
                    cc(x + m, y + m, s * 0.12, if (q) T[0] else T[4]) +
                    V3Pat.quarter(x + s * 0.06, y + s * 0.06, s * 0.2, 0, T[2]) +
                    V3Pat.quarter(x + s * 0.94 - s * 0.2, y + s * 0.06, s * 0.2, 1, T[2]) +
                    V3Pat.quarter(x + s * 0.06, y + s * 0.94 - s * 0.2, s * 0.2, 3, T[2]) +
                    V3Pat.quarter(x + s * 0.94 - s * 0.2, y + s * 0.94 - s * 0.2, s * 0.2, 2, T[2])
            }
        },
        "shapeField" to { c: V3Ctx ->
            val T = V3Pat.tones(c)
            val r = c.r
            val names = r.pick(listOf(listOf("cookie", "sunny", "flower", "clover"), listOf("squircle", "circle", "gem", "pebble"), A.shapeNames))
            val n = r.int(3, 7)
            V3Pat.bg(c, T) + V3Pat.cells(c, n) { x, y, s, _, _ ->
                if (r.chance(0.12)) "" else A.shape(r.pick(names), x + s / 2, y + s / 2, s * r.range(0.3, 0.46), r.range(0.0, 3.0), T[r.int(1, 4)])
            }
        }
    )
}
