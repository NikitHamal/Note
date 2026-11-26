package com.neb.ians.ui.components.m3x

import kotlin.math.min

// Ports of the six v1/v7 compositions the note covers use and profile covers do not: stack, arches, ribbons (concepts/art/art-layouts-b.js),
// capsules, cascade (v7-lay-c.js) and bands (v7-lay-d.js). Same random order as the JS, so a (layout, palette, seed) matches the site.

internal fun M3xBuilder.stack() {
    val n = 6 + fl(r() * 4); val hh = h / n
    for (i in 0 until n) {
        val ww = w * rr(0.45, 0.98); val x = if (r() > 0.5) 0.0 else w - ww
        rc(x - hh, i * hh + hh * 0.08, ww + hh, hh * 0.84, hh * 0.42, 1 + (i % 4))
        if (r() > 0.55) ci(x + (if (x == 0.0) ww - hh * 0.1 else hh * 0.6), i * hh + hh / 2, hh * 0.26, 5)
    }
}

internal fun M3xBuilder.arches() {
    val n = 3 + fl(r() * 3); val ww = w / n
    for (i in 0 until n) for (j in 0 until 3) {
        val iw = ww * (1 - j * 0.28) - ww * 0.1
        val tone = if ((i + j) % 2 == 1) 1 + j else 2 + (if (j > 0) 1 else 0)
        arch(i * ww + (ww - iw) / 2, h - (h * 0.95) * (1 - j * 0.2) + (h * 0.05), iw, h * 0.95 * (1 - j * 0.2), tone)
    }
    ci(rr(0.2, 0.8) * w, h * 0.22, u * 0.1, 5)
}

internal fun M3xBuilder.ribbons() {
    val n = 5 + fl(r() * 3); val a = rr(-0.6, -0.3); val th = u * 0.22
    for (i in 0 until n) {
        val cx = (i / (n - 1.0)) * w * 1.1; val cy = h * rr(0.2, 0.8)
        val rw = w * rr(0.5, 0.9)
        rc(cx - w * 0.4, cy - th / 2, rw, th, th / 2, 1 + (i % 4), a * 57.3, cx, cy)
    }
    ci(rr(0.1, 0.9) * w, rr(0.15, 0.85) * h, u * 0.12, 5)
}

private fun M3xBuilder.pill(cx: Double, cy: Double, pw: Double, ph: Double, tone: Int, deg: Double) =
    rc(cx - pw / 2, cy - ph / 2, pw, ph, min(pw, ph) / 2, tone, deg, cx, cy)

internal fun M3xBuilder.capsules() {
    val n = 9 + fl(r() * 5); val th = u * 0.15; val a = pick(listOf(-38.0, -28.0, 32.0, 42.0))
    for (i in 0 until n) {
        val cx = r() * w; val cy = r() * h; val len = u * rr(0.5, 1.4)
        pill(cx, cy, len, th * rr(0.7, 1.1), 1 + (i % 4), a)
    }
    val x = rr(0.2, 0.85) * w; val y = rr(0.25, 0.75) * h
    pill(x, y, u * 0.5, th * 0.9, 5, a)
}

internal fun M3xBuilder.cascade() {
    val n = 6 + fl(r() * 3); val s = u * 0.52; val dx = (w - s * 0.4) / n; val dy = (h - s * 0.5) / n; val flip = r() > 0.5
    for (i in 0 until n) {
        val x = if (flip) w - s - i * dx else i * dx; val y = i * dy
        rc(x, y, s, s * 0.8, s * 0.28, 1 + (i % 4))
        if (i == n - 2) sh(shapeKind(), x + s * 0.5, y + s * 0.4, s * 0.2, r() * 6, 5)
    }
}

internal fun M3xBuilder.bands() {
    val n = 5 + fl(r() * 3); val a = pick(listOf(-20.0, -14.0, 14.0, 20.0)); val ww = w * 1.6; val hh = h / n * 1.05
    for (i in 0 until n) {
        val rw = ww * rr(0.5, 0.75)
        rc(-w * 0.3 - (i % 2) * w * 0.1, i * (h / n) - hh * 0.1, rw, hh, hh / 2, 1 + (i % 4), a, w / 2, h / 2)
    }
    sh(shapeKind(), rr(0.55, 0.9) * w, rr(0.3, 0.7) * h, u * 0.2, r() * 6, 5)
}
