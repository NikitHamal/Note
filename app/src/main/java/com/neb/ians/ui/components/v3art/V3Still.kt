package com.neb.ians.ui.components.v3art

import com.neb.ians.ui.components.v3art.V3.f
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.min

/** v3-still.js (one study object on a calm backdrop), v3-make.js and the picker of v3-thumb.js. */
internal object V3Still {
    private val CALM = listOf("waves", "shapeField", "scallop", "dhaka", "bubbles", "confetti", "truchet", "lattice", "pills", "arches", "ribbons", "bauhaus")
    private val objects: Map<String, (V3K) -> String> = V3ObjectsA.all + V3ObjectsB.all
    private val patterns: Map<String, (V3Ctx) -> String> = V3PatternsA.all + V3PatternsB.all

    private fun drawObj(name: String, P: V3.Pal, x: Double, y: Double, s: Double, rot: Int): String {
        val fn = objects[name] ?: objects.getValue("books")
        val rotate = if (rot != 0) " rotate($rot)" else ""
        return "<g transform=\"translate(${f(x)} ${f(y)})$rotate scale(${String.format(Locale.US, "%.4f", s / 200)})\">${fn(V3K(P))}</g>"
    }

    private fun decor(c: V3Ctx, cx: Double, cy: Double, avoid: Double, n: Int): String {
        val r = c.r
        val P = c.P
        val cols = listOf(P.sp(0.55), P.t(0.55), P.t(0.8), P.snow)
        val sb = StringBuilder()
        var i = 0
        var tries = 0
        while (i < n && tries < 60) {
            tries++
            val x = r.range(0.05, 0.95) * c.w
            val y = r.range(0.08, 0.92) * c.h
            if (hypot(x - cx, y - cy) < avoid) continue
            val t = r.int(0, 3)
            i++
            sb.append(
                when (t) {
                    0 -> A.star4(x, y, c.h * r.range(0.03, 0.06), cols[r.int(0, 1)])
                    1 -> A.circle(x, y, c.h * r.range(0.008, 0.02), cols[r.int(0, 2)])
                    2 -> A.shape(r.pick(listOf("flower", "cookie", "clover")), x, y, c.h * r.range(0.025, 0.05), r.range(0.0, 3.0), cols[r.int(0, 2)])
                    else -> A.line(x - c.h * 0.02, y, x + c.h * 0.02, y, 3.0, cols[1]) + A.line(x, y - c.h * 0.02, x, y + c.h * 0.02, 3.0, cols[1])
                }
            )
        }
        return sb.toString()
    }

    /** The `still` engine for thumbnails: style is disc, plate, arch or pattern (the sunny `ground` style is not used). */
    private fun still(c: V3Ctx, objs: List<String>, style: String): String {
        val w = c.w
        val h = c.h
        val P = c.P
        val r = c.r
        val m = min(w, h)
        val wide = w / h > 1.6
        val cx = if (wide) w * (if (r.chance(0.7)) 0.74 else 0.5) else w / 2
        val cy = h * 0.5
        val sz = m * (if (wide) 0.66 else 0.6)
        val shp = r.pick(listOf("cookie", "sunny", "cookie12", "puffy", "lotus", "squircle", "pebble", "burst"))
        val sb = StringBuilder()
        if (style == "pattern") sb.append(patterns.getValue(r.pick(CALM))(c))
        else sb.append(A.rect(0.0, 0.0, w, h, 0.0, if (style == "plate") P.t(0.5) else P.t(0.08)))
        when (style) {
            "disc" -> sb.append(A.shape(shp, cx, cy, m * 0.44, r.range(0.0, 3.0), P.t(0.2))).append(A.shape(shp, cx, cy, m * 0.33, r.range(0.0, 3.0), P.t(0.34)))
            "arch" -> sb.append(A.arch(cx - m * 0.4, cy - m * 0.5, m * 0.8, m * 1.2, P.t(0.26))).append(A.arch(cx - m * 0.3, cy - m * 0.4, m * 0.6, m * 1.1, P.t(0.12)))
            else -> sb.append(A.shape(shp, cx, cy, m * 0.4, r.range(0.0, 3.0), P.paper))
        }
        sb.append(decor(c, cx, cy, m * 0.5, if (wide) 9 else 6))
        if (wide && objs.size > 1) {
            sb.append(drawObj(objs[1], P, w * (if (cx > w * 0.6) 0.22 else 0.74), h * 0.62, m * 0.36, -12 + r.int(-6, 6)))
            sb.append(drawObj(objs.getOrElse(2) { objs[0] }, P, w * (if (cx > w * 0.6) 0.4 else 0.9), h * 0.3, m * 0.24, 14))
        }
        return sb.append(drawObj(objs[0], P, cx, cy, sz, 0)).toString()
    }

    class Spec(val id: String, val palette: String, val objs: List<String>, val style: String)

    private fun pal(base: String, k: Int): String {
        val p = V3.palettes
        return if (k % 3 == 0) base else p[(p.indexOf(base) + (k % 3) * 7 + k) % p.size]
    }

    /** The same pick as A.thumbPick in v3-thumb.js: subject words choose the study objects, the hash everything else. */
    fun pick(seed: String, subject: String): Spec {
        val h = V3.hash("v3thumb|$seed|$subject")
        val s = subject.lowercase()
        var name: String? = null
        for (kw in V3Subjects.KW) {
            if (kw.words.any { s.contains(it) }) { name = if (kw.pool.size == 1) kw.pool[0] else kw.pool[((h shr 12) % kw.pool.size).toInt()]; break }
        }
        if (name == null) name = V3Subjects.GENERIC[((h shr 12) % V3Subjects.GENERIC.size).toInt()]
        val d = V3Subjects.SUB.getValue(name)
        val list = d.objects.filter { it !in V3Subjects.NO_OBJ }.toMutableList()
        var i = 0
        while (list.size < 3) { if (V3Subjects.FILL[i] !in list) list.add(V3Subjects.FILL[i]); i++ }
        val rot = ((h shr 3) % 3).toInt()
        val k = ((h shr 6) % 9).toInt()
        return Spec("rt$h", pal(d.palette, k), listOf(list[rot], list[(rot + 1) % 3], list[(rot + 2) % 3]), V3Subjects.STYLES[((h shr 10) % 6).toInt()])
    }

    fun fit(aspect: Float): Float = (Math.round(aspect * 20f) / 20f).coerceIn(0.6f, 4f)

    /** SVG body (no wrapper) and the sky colour for a thumbnail 256 high and 256 x [fit] wide. */
    fun make(spec: Spec, aspectFit: Float, dark: Boolean): Pair<String, String> {
        val P = V3.Pal(spec.palette, dark)
        val c = V3Ctx(Math.round(256 * aspectFit).toDouble(), 256.0, P, V3.Rng(spec.id + ":0"))
        return still(c, spec.objs, spec.style) to P.sky
    }
}
