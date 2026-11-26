package com.neb.ians.ui.components.v3art

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** One drawn element: a path, its fill or pre-allocated stroke style, and any remaining transform (null = already baked into path). */
internal class V3Op(
    val path: Path,
    val fill: Color?,
    val stroke: Color?,
    val strokeStyle: androidx.compose.ui.graphics.drawscope.Stroke?,
    val matrix: Matrix?
)

/** A finished drawing: [w] x [h] units, the sky colour and the elements, built once and drawn many times. */
internal class V3Art(val w: Float, val h: Float, val sky: Color, val ops: List<V3Op>)

/**
 * Reads the small SVG subset the v3 engines write (g, path, circle, ellipse, rect; fill, stroke, stroke-width, transform) into
 * [V3Op]s. Transforms are plain affine math, so nothing depends on the order a matrix class multiplies in.
 */
internal object V3Svg {
    private val TAG = Regex("<(/?)([a-z]+)([^>]*)>")
    private val ATTR = Regex("([a-z-]+)=\"([^\"]*)\"")
    private val FN = Regex("(translate|rotate|scale)\\(([^)]*)\\)")
    private val ARG_SPLIT = Regex("[\\s,]+")
    private val IDENT = doubleArrayOf(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)

    /** m * n for [a, b, c, d, e, f] (SVG matrix order). */
    private fun mul(m: DoubleArray, n: DoubleArray) = doubleArrayOf(
        m[0] * n[0] + m[2] * n[1], m[1] * n[0] + m[3] * n[1],
        m[0] * n[2] + m[2] * n[3], m[1] * n[2] + m[3] * n[3],
        m[0] * n[4] + m[2] * n[5] + m[4], m[1] * n[4] + m[3] * n[5] + m[5]
    )

    private fun transformOf(s: String): DoubleArray {
        var m = IDENT
        for (fn in FN.findAll(s)) {
            val a = fn.groupValues[2].trim().split(ARG_SPLIT).filter { it.isNotEmpty() }.map { it.toDouble() }
            m = when (fn.groupValues[1]) {
                "translate" -> mul(m, doubleArrayOf(1.0, 0.0, 0.0, 1.0, a[0], a.getOrElse(1) { 0.0 }))
                "scale" -> mul(m, doubleArrayOf(a[0], 0.0, 0.0, a.getOrElse(1) { a[0] }, 0.0, 0.0))
                else -> {
                    val r = a[0] * PI / 180
                    val rot = doubleArrayOf(cos(r), sin(r), -sin(r), cos(r), 0.0, 0.0)
                    if (a.size >= 3) mul(mul(mul(m, doubleArrayOf(1.0, 0.0, 0.0, 1.0, a[1], a[2])), rot), doubleArrayOf(1.0, 0.0, 0.0, 1.0, -a[1], -a[2]))
                    else mul(m, rot)
                }
            }
        }
        return m
    }

    private fun color(s: String?): Color? =
        if (s == null || s == "none" || !s.startsWith("#") || s.length != 7) null else Color(("ff" + s.substring(1)).toLong(16))

    private fun matrixOf(m: DoubleArray): Matrix? =
        if (m[0] == 1.0 && m[1] == 0.0 && m[2] == 0.0 && m[3] == 1.0 && m[4] == 0.0 && m[5] == 0.0) null
        else Matrix(
            floatArrayOf(
                m[0].toFloat(), m[1].toFloat(), 0f, 0f,
                m[2].toFloat(), m[3].toFloat(), 0f, 0f,
                0f, 0f, 1f, 0f,
                m[4].toFloat(), m[5].toFloat(), 0f, 1f
            )
        )

    fun parse(body: String, w: Float, h: Float, sky: String): V3Art {
        val ops = ArrayList<V3Op>(256)
        val stack = ArrayList<DoubleArray>()
        var cur = IDENT
        for (t in TAG.findAll(body)) {
            val closing = t.groupValues[1] == "/"
            val name = t.groupValues[2]
            val attrs = HashMap<String, String>()
            for (a in ATTR.findAll(t.groupValues[3])) attrs[a.groupValues[1]] = a.groupValues[2]
            if (name == "g") {
                if (closing) { if (stack.isNotEmpty()) cur = stack.removeAt(stack.size - 1) }
                else { stack.add(cur); cur = mul(cur, transformOf(attrs["transform"] ?: "")) }
                continue
            }
            val own = attrs["transform"]?.let { mul(cur, transformOf(it)) } ?: cur
            fun num(k: String) = attrs[k]?.toFloatOrNull() ?: 0f
            val path: Path = when (name) {
                "path" -> PathParser().parsePathString(attrs["d"] ?: continue).toPath()
                "circle" -> Path().apply { addOval(Rect(num("cx") - num("r"), num("cy") - num("r"), num("cx") + num("r"), num("cy") + num("r"))) }
                "ellipse" -> Path().apply { addOval(Rect(num("cx") - num("rx"), num("cy") - num("ry"), num("cx") + num("rx"), num("cy") + num("ry"))) }
                "rect" -> Path().apply {
                    val x = num("x"); val y = num("y"); val rw = num("width"); val rh = num("height")
                    val rx = minOf(num("rx"), rw / 2f, rh / 2f)
                    if (rx > 0f) addRoundRect(RoundRect(x, y, x + rw, y + rh, CornerRadius(rx, rx))) else addRect(Rect(x, y, x + rw, y + rh))
                }
                else -> continue
            }
            val fill = color(attrs["fill"])
            val stroke = color(attrs["stroke"])
            if (fill == null && stroke == null) continue
            val rawMat = matrixOf(own)
            val sx = kotlin.math.hypot(own[0], own[1])
            val sy = kotlin.math.hypot(own[2], own[3])
            val uniform = kotlin.math.abs(own[0] * own[2] + own[1] * own[3]) < 1e-3 &&
                kotlin.math.abs(sx - sy) < 1e-3 * maxOf(1.0, sx)
            val bakeMat = rawMat != null && (stroke == null || uniform)
            val mat = if (bakeMat) {
                path.transform(rawMat!!)
                null
            } else {
                rawMat
            }
            val strokeStyle = if (stroke != null) {
                val baseW = attrs["stroke-width"]?.toFloatOrNull() ?: 1f
                androidx.compose.ui.graphics.drawscope.Stroke(
                    width = if (bakeMat) baseW * sx.toFloat() else baseW,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            } else null
            ops.add(V3Op(path, fill, stroke, strokeStyle, mat))
        }
        return V3Art(w, h, color(sky) ?: Color.White, ops)
    }
}
