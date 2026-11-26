package com.neb.ians.ui.components.m3x

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// Ports of concepts/art-v7/v7-lay-c.js (swirl, ripple, venn) and v7-lay-d.js. Same random order as the JS.

internal fun M3xBuilder.swirl() {
    val cx = rr(0.45, 0.8) * w; val cy = rr(0.35, 0.65) * h; val n = 22; val k = shapeKind(); val rad = u * 0.95
    for (i in n downTo 0) {
        val a = i * 0.62; val d = rad * (i.toDouble() / n).pow(0.8) * 0.9
        sh(k, cx + cos(a) * d * 0.55, cy + sin(a) * d * 0.55, rad * (0.06 + 0.14 * (i.toDouble() / n)), a, if (i == 0) 5 else 1 + i % 4)
    }
}

internal fun M3xBuilder.ripple() {
    val cx = pick(listOf(0.1, 0.9, 0.5)) * w; val cy = pick(listOf(0.0, 1.0, 0.5)) * h; val n = 7; val rad = hypot(w, h) * 0.85
    for (i in n downTo 1) ci(cx, cy, rad * i / n, 1 + i % 4)
    ci(cx, cy, rad * 0.07, 5)
    sh(shapeKind(), rr(0.3, 0.8) * w, rr(0.3, 0.7) * h, u * 0.14, r() * 6, 3)
}

internal fun M3xBuilder.venn() {
    val cx = rr(0.45, 0.75) * w; val cy = h * 0.5; val rad = u * rr(0.34, 0.46); val n = pick(listOf(3, 4)); val a0 = r() * 6
    for (i in 0 until n) ci(cx + cos(a0 + i * 6.283 / n) * rad * 0.62, cy + sin(a0 + i * 6.283 / n) * rad * 0.62, rad, 1 + i % 3, 0.78)
    ci(cx, cy, rad * 0.22, 5)
    sh(shapeKind(), rr(0.05, 0.25) * w, rr(0.6, 1.0) * h, u * 0.2, r() * 6, 3)
}

internal fun M3xBuilder.truchet() {
    val s = u / pick(listOf(2, 3, 4))
    var y = 0
    while (y * s < h) {
        var x = 0
        while (x * s < w) {
            val q = fl(r() * 4); val t = r()
            rc(x * s, y * s, s, s, 0.0, if (t < 0.5) 1 else 6)
            quarter(x * s, y * s, s, q, 2 + fl(r() * 2))
            quarter(x * s + s * 0.5, y * s + s * 0.5, s * 0.5, q + 2, if (t > 0.85) 5 else 4)
            x++
        }
        y++
    }
}

internal fun M3xBuilder.contour() {
    val n = 9 + fl(r() * 5); val sw = u * 0.045; val ph = r() * 6; val k = rr(0.8, 1.8)
    for (i in 0 until n) {
        val pts = (0..14).map { j -> doubleArrayOf(j / 14.0 * w, h * (i.toDouble() / (n - 1)) + sin(j / 14.0 * 6.283 * k + ph + i * 0.28) * h * 0.2) }
        curve(pts, sw, if (i % 5 == 3) 5 else 1 + i % 3)
    }
}

internal fun M3xBuilder.blobs() {
    val n = 5 + fl(r() * 3)
    for (i in 0 until n) sh(shapeKind(), rr(0.05, 0.95) * w, rr(0.1, 0.9) * h, u * rr(0.3, 0.62), r() * 6.28, 1 + i % 4, 0.9)
    sh(shapeKind(), rr(0.5, 0.9) * w, rr(0.25, 0.7) * h, u * 0.17, r() * 6, 5)
}

internal fun M3xBuilder.rings() {
    val cx = rr(0.55, 0.85) * w; val cy = rr(0.35, 0.65) * h; val n = 6; val rad = u * 0.9; val sw = rad / n * 0.5
    for (i in 0 until n) {
        val a = r() * 6.28; val rr0 = rad * (i + 1.4) / (n + 0.6)
        ring(cx, cy, rr0, sw, 1 + i % 2, 0.8)
        arc(cx, cy, rr0, a, a + rr(1.8, 4.6), sw, if (i == n - 2) 5 else 3)
    }
    sh(shapeKind(), rr(0.05, 0.3) * w, rr(0.6, 1.0) * h, u * 0.2, r() * 6, 2)
}

internal fun M3xBuilder.bento() {
    val gp = u * 0.03; val cw = w / 4; val ch = h / 3
    val spans = listOf(intArrayOf(0, 0, 2, 2), intArrayOf(2, 0, 1, 1), intArrayOf(3, 0, 1, 2), intArrayOf(2, 1, 1, 2), intArrayOf(0, 2, 2, 1), intArrayOf(3, 2, 1, 1))
    spans.forEachIndexed { i, a ->
        val x = a[0] * cw + gp; val y = a[1] * ch + gp; val bw = a[2] * cw - gp * 2; val bh = a[3] * ch - gp * 2
        rc(x, y, bw, bh, min(bw, bh) * 0.22, if (i == 2) 3 else 1 + i % 4)
        if (i % 2 == 0) sh(shapeKind(), x + bw / 2, y + bh / 2, min(bw, bh) * 0.3, r() * 6, if (i == 4) 5 else if (i == 0) 3 else 4)
    }
}

internal fun M3xBuilder.diamonds() {
    val s = u / pick(listOf(3, 4, 5))
    var y = 0
    while (y * s * 0.5 < h + s) {
        var x = -1
        while (x * s < w + s) {
            val ox = (y % 2) * s * 0.5; val t = r(); val cx = x * s + ox + s / 2; val cy = y * s * 0.5
            rc(cx - s * 0.3, cy - s * 0.3, s * 0.6, s * 0.6, s * 0.14, if (t < 0.1) 5 else 1 + fl(t * 4.4) % 4, 45.0, cx, cy)
            x++
        }
        y++
    }
}

internal fun M3xBuilder.segments() {
    val n = pick(listOf(3, 4, 5)); val cx = rr(0.55, 0.85) * w; val cy = h * 0.5; val rad = u * 0.85; val a0 = r() * 6.28
    for (i in 0 until n) {
        val a = a0 + i * 6.283 / n; val sweep = 6.283 / n * 0.92; val rd = rad * (0.62 + 0.38 * r())
        wedge(cx, cy, rd, a * 180 / PI, sweep * 180 / PI, 1 + i % 4)
    }
    ci(cx, cy, rad * 0.2, 5)
    sh(shapeKind(), rr(0.05, 0.3) * w, rr(0.6, 1.0) * h, u * 0.2, r() * 6, 3)
}

internal fun M3xBuilder.petalfield() {
    val cols = pick(listOf(3, 4, 5)); val s = u / cols * 1.15; val k = pick(listOf("flower", "cookie", "clover", "sunny"))
    var y = 0
    while (y * s * 0.85 < h + s) {
        var x = 0
        while (x * s < w + s) {
            val cx = x * s + (y % 2) * s / 2; val cy = y * s * 0.85; val t = r()
            sh(k, cx, cy, s * 0.46, r() * 6, 1 + fl(t * 4))
            if (t > 0.88) ci(cx, cy, s * 0.1, 5)
            x++
        }
        y++
    }
}

/** The 22 layouts a profile cover can use (v7 minus ribbons, arches, graph, net, stacks, bars, capsules, cascades, bands). */
object M3xLayouts {
    fun build(layout: String, key: String, w: Double, h: Double): M3xPiece {
        val b = M3xBuilder(key, w, h)
        when (layout) {
            "orbit" -> b.orbit(); "bauhaus" -> b.bauhaus(); "waves" -> b.waves(); "scatter" -> b.scatter(); "dots" -> b.dots()
            "petals" -> b.petals(); "mosaic" -> b.mosaic(); "folds" -> b.folds(); "tiles" -> b.tiles(); "pinwheel" -> b.pinwheel()
            "mondrian" -> b.mondrian(); "swirl" -> b.swirl(); "ripple" -> b.ripple(); "venn" -> b.venn(); "truchet" -> b.truchet()
            "contour" -> b.contour(); "blobs" -> b.blobs(); "rings" -> b.rings(); "bento" -> b.bento(); "diamonds" -> b.diamonds()
            "segments" -> b.segments(); "petalfield" -> b.petalfield()
            // used by note covers only (the profile manifest never names them)
            "stack" -> b.stack(); "arches" -> b.arches(); "ribbons" -> b.ribbons()
            "capsules" -> b.capsules(); "cascade" -> b.cascade(); "bands" -> b.bands()
            else -> b.orbit()
        }
        return M3xPiece(w.toFloat(), h.toFloat(), b.ops)
    }
}
