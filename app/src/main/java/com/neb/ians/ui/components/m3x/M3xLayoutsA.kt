package com.neb.ians.ui.components.m3x

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.sin

// Ports of concepts/art/art-layouts-a.js, art-layouts-b.js and concepts/art-v7/v7-lay-c.js. Every random call is made in the same
// order as in the JS, so a (layout, palette, seed) is the same picture here and on the site. Tones: 0 page .. 5 accent.

internal fun M3xBuilder.orbit() {
    val k = shapeKind(); val cx = rr(0.55, 0.95) * w; val cy = rr(0.1, 0.9) * h; val rad = u * rr(0.75, 1.05); val rot = r() * 6.28
    for (i in 5 downTo 1) sh(k, cx, cy, rad * i / 5, rot + i * 0.12, if (i % 2 == 1) 1 else 2, if (i == 1) 1.0 else 0.45 + i * 0.1)
    sh(k, cx, cy, rad * 0.2, rot, 3)
    ci(rr(0.08, 0.3) * w, rr(0.2, 0.8) * h, u * 0.09, 5)
    sh(shapeKind(), rr(0.05, 0.4) * w, rr(0.55, 1.05) * h, u * 0.34, r() * 6, 2)
}

internal fun M3xBuilder.bauhaus() {
    val s = h / (if (r() > 0.5) 2 else 3); val cols = ceil(w / s).toInt() + 1; val rows = ceil(h / s).toInt()
    for (y in 0 until rows) for (x in 0 until cols) {
        val t = r(); val q = fl(r() * 4); val f = 1 + fl(r() * 5)
        if (t < 0.28) quarter(x * s, y * s, s, q, f)
        else if (t < 0.46) ci(x * s + s / 2, y * s + s / 2, s * 0.42, f)
        else if (t < 0.62) arch(x * s + s * 0.1, y * s + s * 0.05, s * 0.8, s * 0.9, f)
        else if (t < 0.74) rc(x * s + s * 0.1, y * s + s * 0.3, s * 0.8, s * 0.4, s * 0.2, f)
        else if (t < 0.82) sh(shapeKind(), x * s + s / 2, y * s + s / 2, s * 0.42, r() * 6, 5)
    }
}

internal fun M3xBuilder.waves() {
    val n = 4 + fl(r() * 3)
    for (i in 0 until n) {
        val y = h * (0.3 + (i.toDouble() / n) * 0.62); val a = h * rr(0.05, 0.12); val ph = r() * 6.28; val k = rr(1.0, 2.6)
        path(1 + i % 4) {
            moveTo(0f, h.toFloat()); lineTo(0f, y.toFloat())
            for (j in 0..48) lineTo((j / 48.0 * w).toFloat(), (y + sin(j / 48.0 * 6.28 * k + ph) * a).toFloat())
            lineTo(w.toFloat(), h.toFloat()); close()
        }
        if (i == 1) sh(pick(listOf("squircle", "pebble", "clover")), rr(0.15, 0.85) * w, y + h * 0.1, h * 0.12, r() * 6, 5)
    }
}

internal fun M3xBuilder.scatter() {
    val n = 9 + fl(r() * 5)
    for (i in 0 until n) sh(shapeKind(), r() * w, r() * h, u * rr(0.1, 0.42), r() * 6.28, 1 + i % 5)
    sh(shapeKind(), rr(0.5, 0.9) * w, rr(0.3, 0.7) * h, u * 0.3, r() * 6, 3)
    ci(rr(0.1, 0.5) * w, rr(0.2, 0.8) * h, u * 0.08, 4)
}

internal fun M3xBuilder.dots() {
    val s = u / 10; val fx = rr(0.3, 1.0) * w; val fy = rr(0.0, 1.0) * h; val rad = hypot(w, h) * 0.7
    sh(shapeKind(), fx, fy, u * 0.3, r() * 6, 5)
    var y = s / 2
    while (y < h) {
        val odd = Math.floor(y / s + 0.5).toLong() % 2
        var x = s / 2 + (odd * s) / 2
        while (x < w) {
            val d = hypot(x - fx, y - fy) / rad
            ci(x, y, maxOf(0.6, s * 0.46 * (1 - d)), if (d < 0.5) 3 else 2, 0.9)
            x += s
        }
        y += s
    }
}

internal fun M3xBuilder.petals() {
    val k = pick(listOf("flower", "cookie", "sunny", "clover")); val cx = rr(0.58, 0.88) * w; val cy = rr(0.3, 0.7) * h; val rad = u * rr(0.7, 0.95); val n = 6
    for (i in 0 until n) sh(k, cx, cy, rad * (1 - i.toDouble() / n), i * 0.35, if (i % 2 == 1) 2 else 1 + i % 4)
    ci(cx, cy, rad * 0.1, 5)
    sh(shapeKind(), rr(0.05, 0.3) * w, rr(0.6, 1.0) * h, u * 0.25, r() * 6, 3)
}

internal fun M3xBuilder.mosaic() {
    val s = u / pick(listOf(3, 4, 5)); val gp = s * 0.08
    var y = 0
    while (y * s < h) {
        var x = 0
        while (x * s < w) {
            val t = r(); val cx = x * s + s / 2; val cy = y * s + s / 2
            if (t < 0.55) sh(pick(listOf("squircle", "pebble", "cookie")), cx, cy, s / 2 - gp, r() * 6, 1 + fl(r() * 4))
            else if (t < 0.7) sh(shapeKind(), cx, cy, s / 2 - gp, r() * 6, 5)
            x++
        }
        y++
    }
}

internal fun M3xBuilder.folds() {
    val n = 4 + fl(r() * 3); var x = 0.0
    for (i in 0 until n) {
        val fw = w * rr(0.25, 0.5); r(); r() // the JS draws two unused heights; kept so the sequence matches
        poly(listOf(doubleArrayOf(x, 0.0), doubleArrayOf(x + fw, 0.0), doubleArrayOf(x + fw * 0.3, h), doubleArrayOf(x - fw * 0.7, h)), 1 + i % 4)
        x += fw * 0.7
    }
    sh(shapeKind(), rr(0.2, 0.8) * w, rr(0.3, 0.7) * h, u * 0.2, r() * 6, 5)
    sh(shapeKind(), rr(0.2, 0.9) * w, rr(0.2, 0.9) * h, u * 0.12, r() * 6, 3)
}

internal fun M3xBuilder.tiles() {
    val s = u / pick(listOf(2, 3))
    var y = 0
    while (y * s < h) {
        var x = 0
        while (x * s < w) {
            val q = (x + y * 2 + fl(r() * 2)) % 4
            rc(x * s, y * s, s, s, 0.0, 1 + q % 2)
            quarter(x * s, y * s, s, q, 3)
            ci(x * s + s * (if (q % 2 == 1) 0.74 else 0.26), y * s + s * (if (q > 1) 0.26 else 0.74), s * 0.1, 5)
            x++
        }
        y++
    }
}

internal fun M3xBuilder.pinwheel() {
    val cx = rr(0.5, 0.85) * w; val cy = rr(0.35, 0.65) * h; val rad = u * rr(0.7, 0.95); val n = pick(listOf(4, 6, 8))
    for (i in 0 until n) wedge(cx, cy, rad, 0.0, 6.283 / n * 180 / PI, 1 + i % 4, i * 360.0 / n)
    ci(cx, cy, rad * 0.16, 5)
    sh(shapeKind(), rr(0.05, 0.3) * w, rr(0.6, 1.0) * h, u * 0.22, r() * 6, 3)
}

internal fun M3xBuilder.mondrian() {
    val gp = u * 0.02; val cells = mutableListOf(doubleArrayOf(0.0, 0.0, w, h)); var i = 0
    while (true) {
        if (i >= 7 + fl(r() * 4)) break // the JS loop test draws a number every pass
        val j = fl(r() * cells.size); val a = cells[j]; val v = a[2] > a[3] * rr(0.8, 1.4); val t = rr(0.35, 0.65)
        if (a[2] < u * 0.3 || a[3] < u * 0.3) { i++; continue }
        cells.removeAt(j)
        if (v) cells.addAll(j, listOf(doubleArrayOf(a[0], a[1], a[2] * t, a[3]), doubleArrayOf(a[0] + a[2] * t, a[1], a[2] * (1 - t), a[3])))
        else cells.addAll(j, listOf(doubleArrayOf(a[0], a[1], a[2], a[3] * t), doubleArrayOf(a[0], a[1] + a[3] * t, a[2], a[3] * (1 - t))))
        i++
    }
    cells.forEachIndexed { n, a ->
        val k = r()
        rc(a[0] + gp, a[1] + gp, a[2] - gp * 2, a[3] - gp * 2, u * 0.07, if (k < 0.12) 5 else if (k < 0.3) 3 else 1 + n % 3)
    }
}
