package com.neb.ians.ui.components.v3art

import com.neb.ians.ui.components.v3art.V3.f
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.sin

internal object V3PatternsB {
    private fun rr(x: Double, y: Double, w: Double, h: Double, rx: Double, fill: String, ex: String? = null) = A.rect(x, y, w, h, rx, fill, ex)
    private fun cc(x: Double, y: Double, r: Double, fill: String) = A.circle(x, y, r, fill)

    val all: Map<String, (V3Ctx) -> String> = mapOf(
        "scallop" to { c: V3Ctx ->
            val T = V3Pat.tones(c)
            val r = c.r
            val n = r.int(5, 9)
            val s = c.h / n
            var o = rr(0.0, 0.0, c.w, c.h, 0.0, T[0])
            for (i in n * 2 downTo 0) {
                var j = -1
                while (j * s < c.w + s) {
                    val cx = j * s + (if (i % 2 == 1) s / 2 else 0.0)
                    val cy = i * s * 0.5
                    o += A.path("M${f(cx - s / 2)} ${f(cy)}A${f(s / 2)} ${f(s / 2)} 0 0 0 ${f(cx + s / 2)} ${f(cy)}Z", c.P.t(0.1 + (i.toDouble() / (n * 2)) * 0.7)) + cc(cx, cy + s * 0.1, s * 0.07, T[4])
                    j++
                }
            }
            o
        },
        "pills" to { c: V3Ctx ->
            val T = V3Pat.tones(c)
            val r = c.r
            val n = r.int(7, 12)
            val b = c.h / n
            var o = V3Pat.bg(c, T)
            for (i in 0 until n) {
                var x = -r.range(0.0, 0.3) * c.w
                while (x < c.w) {
                    val l = r.range(0.08, 0.36) * c.w
                    o += rr(x, i * b + b * 0.12, l, b * 0.76, b * 0.38, T[r.int(1, 4)])
                    x += l + b * 0.3
                }
            }
            o
        },
        "bubbles" to { c: V3Ctx ->
            val T = V3Pat.tones(c)
            val r = c.r
            var o = V3Pat.bg(c, T)
            val pts = ArrayList<DoubleArray>()
            var tries = 0
            val N = V3.jsRound(c.w * c.h / 2600).toInt()
            while (pts.size < N && tries++ < 4000) {
                val rad = r.range(0.015, 0.15) * c.h
                val x = r.range(0.0, 1.0) * c.w
                val y = r.range(0.0, 1.0) * c.h
                var ok = true
                for (p in pts) if (hypot(p[0] - x, p[1] - y) < p[2] + rad + 3) { ok = false; break }
                if (ok) {
                    pts.add(doubleArrayOf(x, y, rad))
                    o += cc(x, y, rad, T[r.int(1, 4)]) + (if (rad > c.h * 0.06) cc(x, y, rad * 0.5, T[r.int(0, 3)]) else "")
                }
            }
            o
        },
        "waves" to { c: V3Ctx ->
            val r = c.r
            var o = ""
            val n = r.int(7, 11)
            val ph = r.range(0.0, 6.0)
            val fr = r.range(1.0, 3.0) / c.w * 6.28
            val amp = c.h * r.range(0.03, 0.09)
            o += rr(0.0, 0.0, c.w, c.h, 0.0, c.P.t(0.06))
            for (i in 0 until n) {
                val pts = ArrayList<DoubleArray>()
                var x = -20.0
                while (x <= c.w + 40) { pts.add(A.pt(x, c.h * (i + 0.5) / n + sin(x * fr + ph + i * 0.7) * amp)); x += 16 }
                o += A.fillTo(pts, c.h * 1.1, c.P.t(0.1 + i.toDouble() / n * 0.72))
            }
            o
        },
        "bauhaus" to { c: V3Ctx ->
            val T = V3Pat.tones(c)
            val r = c.r
            val n = r.int(3, 6)
            V3Pat.bg(c, T) + V3Pat.cells(c, n) { x, y, s, _, _ ->
                val t = r.int(0, 6)
                val a = r.pick(T.drop(1))
                val m = s / 2
                when (t) {
                    0 -> V3Pat.quarter(x, y, s, r.int(0, 3), a)
                    1 -> cc(x + m, y + m, s * 0.42, a)
                    2 -> A.arch(x + s * 0.1, y + s * 0.1, s * 0.8, s * 0.9, a)
                    3 -> rr(x + s * 0.1, y + s * 0.1, s * 0.8, s * 0.8, s * 0.28, a)
                    4 -> A.path(A.ngon(4, x + m, y + m, s * 0.46, 0.0, s * 0.1), a)
                    5 -> cc(x + m, y + m, s * 0.4, a) + cc(x + m, y + m, s * 0.17, r.pick(T))
                    else -> ""
                }
            }
        },
        "ribbons" to { c: V3Ctx ->
            val r = c.r
            var o = rr(0.0, 0.0, c.w, c.h, 0.0, c.P.t(0.06))
            val n = r.int(6, 9)
            val a = r.range(-0.4, 0.4)
            for (i in 0 until n) {
                val y = c.h * (i + 0.5) / n
                val pts = ArrayList<DoubleArray>()
                var x = -40.0
                while (x <= c.w + 60) { pts.add(A.pt(x, y + sin(x / c.w * 5 + i * 0.7 + a) * c.h * 0.16 + (x / c.w - 0.5) * a * c.h)); x += c.w / 6 }
                o += A.stroke(A.smooth(pts, false), c.h / n * 1.25, if (i == n - 2) c.P.sp(0.55) else c.P.t(0.14 + i.toDouble() / n * 0.7))
            }
            o
        },
        "arches" to { c: V3Ctx ->
            val T = V3Pat.tones(c)
            val r = c.r
            val n = r.int(2, 4)
            val s = c.h / n * 0.96
            val cols = ceil(c.w / (s * 0.9)).toInt() + 1
            var o = V3Pat.bg(c, T)
            for (j in 0 until n + 1) for (i in 0 until cols) for (k in 0 until 4) {
                val w = s * 0.82 * (1 - k * 0.22)
                val x = i * s * 0.9 + s * 0.04 + (s * 0.82 - w) / 2 + (if (j % 2 == 1) s * 0.45 else 0.0) - s * 0.4
                val y = j * s * 0.96 + s * 0.2 + (s * 0.82 - w) / 2 - s * 0.2
                o += A.arch(x, y, w, s * 0.9 - (s * 0.82 - w) / 2, if (k == 3) T[4] else c.P.t(0.2 + k * 0.22 + (j % 3) * 0.05))
            }
            o
        },
        "confetti" to { c: V3Ctx ->
            val T = V3Pat.tones(c)
            val r = c.r
            var o = V3Pat.bg(c, T)
            val N = V3.jsRound(c.w * c.h / 9000).toInt()
            for (i in 0 until N) {
                val x = r.range(0.0, 1.0) * c.w
                val y = r.range(0.0, 1.0) * c.h
                val s = c.h * r.range(0.015, 0.05)
                val t = r.int(0, 4)
                val col = r.pick(T.drop(1))
                o += when (t) {
                    0 -> cc(x, y, s, col)
                    1 -> A.star4(x, y, s * 1.5, col)
                    2 -> rr(x, y, s * 3, s, s / 2, col, "transform=\"rotate(${r.int(0, 170)} ${f(x)} ${f(y)})\"")
                    3 -> A.shape(r.pick(listOf("flower", "cookie", "clover")), x, y, s * 1.4, r.range(0.0, 3.0), col)
                    else -> A.line(x - s, y, x + s, y, s * 0.5, col) + A.line(x, y - s, x, y + s, s * 0.5, col)
                }
            }
            o
        }
    )
}
