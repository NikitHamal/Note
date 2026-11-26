package com.neb.ians.ui.components.v3art

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Port of concepts/art-v3/v3-core.js (the part resource thumbnails need): seeded random, OKLCH tonal palettes and the
 * SVG-string helpers. The engines in this package are line-for-line ports of the JS ones, so the same seed draws the same piece
 * on the web (js/v3-thumb.js) and here. The strings are parsed once into draw ops by [V3Svg].
 */
internal object V3 {
    fun hash(s: String): Long {
        var h = 2166136261L.toInt()
        for (ch in s) { h = h xor ch.code; h *= 16777619 }
        return h.toLong() and 0xFFFFFFFFL
    }

    /** Math.round of JS: halves go up. */
    fun jsRound(v: Double): Double = floor(v + 0.5)

    fun f(v: Double): String {
        val r = jsRound(v * 10) / 10
        return if (r == floor(r) && abs(r) < 1e9) r.toLong().toString() else r.toString()
    }

    class Rng(seed: String) {
        private var a: Int = hash(seed).toInt()
        fun next(): Double {
            a += 0x6D2B79F5
            var t = (a xor (a ushr 15)) * (1 or a)
            t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
            return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
        }
        fun range(lo: Double, hi: Double) = lo + (hi - lo) * next()
        fun int(lo: Int, hi: Int): Int = floor(lo + (hi - lo + 1) * next()).toInt()
        fun <T> pick(l: List<T>): T = l[floor(next() * l.size).toInt()]
        fun chance(p: Double) = next() < p
    }

    private fun lin(l: Double, c: Double, hue: Double): DoubleArray {
        val h = hue * Math.PI / 180
        val a = c * cos(h)
        val b = c * sin(h)
        val l3 = (l + 0.3963377774 * a + 0.2158037573 * b).pow(3)
        val m3 = (l - 0.1055613458 * a - 0.0638541728 * b).pow(3)
        val s3 = (l - 0.0894841775 * a - 1.291485548 * b).pow(3)
        return doubleArrayOf(
            4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3,
            -1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3,
            -0.0041960863 * l3 - 0.7034186147 * m3 + 1.707614701 * s3
        )
    }

    fun oklch(l: Double, c0: Double, h: Double): String {
        var c = c0
        var v = lin(l, c, h)
        var n = 0
        while (n++ < 14 && (min(v[0], min(v[1], v[2])) < -0.002 || max(v[0], max(v[1], v[2])) > 1.002)) { c *= 0.93; v = lin(l, c, h) }
        val sb = StringBuilder("#")
        for (x0 in v) {
            var x = max(0.0, min(1.0, x0))
            x = if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1 / 2.4) - 0.055
            val i = jsRound(x * 255).toInt().coerceIn(0, 255)
            sb.append("0123456789abcdef"[i shr 4]).append("0123456789abcdef"[i and 15])
        }
        return sb.toString()
    }

    /** name, hue, chroma, spark hue. 20 families, nothing between 285 and 335 (no purple). */
    internal class Fam(val name: String, val h: Double, val c: Double, val sh: Double)
    private val FAM = listOf(
        Fam("brand", 262.0, 0.20, 75.0), Fam("cobalt", 255.0, 0.19, 55.0), Fam("sky", 232.0, 0.15, 40.0), Fam("aqua", 205.0, 0.13, 30.0),
        Fam("teal", 188.0, 0.12, 40.0), Fam("emerald", 160.0, 0.14, 70.0), Fam("mint", 150.0, 0.12, 20.0), Fam("moss", 128.0, 0.13, 55.0),
        Fam("lime", 112.0, 0.16, 28.0), Fam("sun", 92.0, 0.16, 250.0), Fam("amber", 72.0, 0.16, 235.0), Fam("marigold", 60.0, 0.17, 225.0),
        Fam("terracotta", 42.0, 0.14, 215.0), Fam("coral", 28.0, 0.17, 205.0), Fam("rose", 8.0, 0.15, 190.0), Fam("crimson", 20.0, 0.19, 175.0),
        Fam("slate", 250.0, 0.045, 60.0), Fam("sand", 85.0, 0.05, 235.0), Fam("pine", 168.0, 0.09, 55.0), Fam("denim", 245.0, 0.12, 70.0)
    )
    val palettes: List<String> = FAM.map { it.name }

    class Pal(name: String, val dark: Boolean) {
        private val fam = FAM.firstOrNull { it.name == name } ?: FAM[0]
        private val h = fam.h
        private val c = fam.c
        private fun ch(k: Double) = c * max(0.3, 1 - abs(k - 0.58) * 1.25)
        fun t(k0: Double): String {
            val k = max(0.0, min(1.0, k0))
            return if (dark) oklch(0.17 + 0.71 * k, ch(k) * 0.62, h) else oklch(0.972 - 0.7 * k, ch(k), h)
        }
        fun sp(k0: Double): String {
            val k = max(0.0, min(1.0, k0))
            return if (dark) oklch(0.25 + 0.62 * k, max(0.05, 0.15 * max(0.4, 1 - abs(k - 0.7))), fam.sh)
            else oklch(0.95 - 0.55 * k, max(0.06, 0.16 * max(0.4, 1 - abs(k - 0.5) * 1.2)), fam.sh)
        }
        val sky: String = if (dark) oklch(0.2, c * 0.22, h) else oklch(0.965, c * 0.18, h)
        val paper: String = if (dark) oklch(0.26, ch(0.2) * 0.3, h) else "#ffffff"
        val snow: String = if (dark) oklch(0.84, 0.03, h) else "#ffffff"
    }
}
