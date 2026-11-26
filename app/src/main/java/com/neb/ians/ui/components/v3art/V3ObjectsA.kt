package com.neb.ians.ui.components.v3art

import com.neb.ians.ui.components.v3art.V3.f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The colours a study object is drawn with (K() in v3-objects-a.js). */
internal class V3K(P: V3.Pal) {
    val a = P.t(0.56)
    val b = P.t(0.84)
    val c = P.t(0.22)
    val m = P.t(0.38)
    val d = P.sp(0.55)
    val p = P.paper
    val s = P.sp(0.3)
}

// Number-friendly wrappers so the object drawings read like the JS ones.
internal fun R(x: Number, y: Number, w: Number, h: Number, rx: Number, fill: String, ex: String? = null) =
    A.rect(x.toDouble(), y.toDouble(), w.toDouble(), h.toDouble(), rx.toDouble(), fill, ex)
internal fun C(x: Number, y: Number, r: Number, fill: String) = A.circle(x.toDouble(), y.toDouble(), r.toDouble(), fill)
internal fun S(d: String, sw: Number, stroke: String) = A.stroke(d, sw.toDouble(), stroke)
internal fun PA(d: String, fill: String, ex: String? = null) = A.path(d, fill, ex)
internal fun SH(k: String, x: Number, y: Number, r: Number, rot: Number, fill: String) = A.shape(k, x.toDouble(), y.toDouble(), r.toDouble(), rot.toDouble(), fill)
internal fun ST4(x: Number, y: Number, r: Number, fill: String) = A.star4(x.toDouble(), y.toDouble(), r.toDouble(), fill)
internal fun PL(fill: String, vararg xy: Number, ex: String? = null) =
    A.poly(List(xy.size / 2) { A.pt(xy[it * 2].toDouble(), xy[it * 2 + 1].toDouble()) }, fill, ex)
internal fun RP(rad: Number, vararg xy: Number) = A.rpoly(List(xy.size / 2) { A.pt(xy[it * 2].toDouble(), xy[it * 2 + 1].toDouble()) }, rad.toDouble())
internal fun tf(s: String) = "transform=\"$s\""

internal object V3ObjectsA {
    val all: Map<String, (V3K) -> String> = mapOf(
        "books" to { k: V3K ->
            R(-80, 40, 160, 44, 10, k.a) + R(-80, 50, 160, 6, 0, k.c) + R(-66, -2, 138, 42, 10, k.d) + R(-66, 8, 138, 6, 0, k.p) +
                R(-76, -44, 150, 42, 10, k.b) + R(-76, -34, 150, 6, 0, k.m) + R(40, -44, 14, 66, 3, k.c) + PL(k.p, 40, 22, 47, 12, 54, 22)
        },
        "atom" to { k: V3K ->
            var o = ""
            for (i in 0 until 3) o += "<ellipse cx=\"0\" cy=\"0\" rx=\"88\" ry=\"32\" transform=\"rotate(${i * 60})\" fill=\"none\" stroke=\"${if (i == 1) k.m else k.a}\" stroke-width=\"9\"/>"
            o + C(0, 0, 22, k.d) + C(82, 0, 11, k.b) + C(-41, -71, 11, k.b) + C(-41, 71, 11, k.b)
        },
        "flask" to { k: V3K ->
            R(-22, -88, 44, 60, 6, k.c) + R(-32, -92, 64, 14, 7, k.m) + PA("M-22 -30 L-84 60 Q-92 82 -66 84 H66 Q92 82 84 60 L22 -30Z", k.c) +
                PA("M-50 22 L-84 60 Q-92 82 -66 84 H66 Q92 82 84 60 L50 22Q0 38 -50 22Z", k.d) + C(-12, 52, 9, k.p) + C(20, 38, 6, k.p) + C(6, 62, 5, k.p) + C(0, -52, 7, k.a)
        },
        "microscope" to { k: V3K ->
            R(-70, 70, 140, 20, 10, k.b) + S("M30 70 Q86 30 52 -30", 14, k.a) + R(-34, -88, 30, 100, 12, k.m, tf("rotate(24 -19 -38)")) +
                R(-6, -100, 34, 12, 4, k.b, tf("rotate(24 11 -94)")) + R(-60, 40, 76, 12, 6, k.d) + C(-40, 10, 12, k.p) + R(-52, 10, 4, 4, 0, k.p)
        },
        "globe" to { k: V3K ->
            S("M-70 80 Q0 120 70 80", 10, k.b) + R(-34, 86, 68, 12, 6, k.b) + C(0, -6, 78, k.a) + SH("pebble", -22, -30, 30, 0.5, k.c) +
                SH("pebble", 30, 14, 26, 2, k.c) + SH("pebble", -34, 30, 16, 1, k.c) + S("M0 -84 V72", 4, k.p) +
                "<ellipse cx=\"0\" cy=\"-6\" rx=\"36\" ry=\"78\" fill=\"none\" stroke=\"${k.p}\" stroke-width=\"4\"/>"
        },
        "laptop" to { k: V3K ->
            R(-78, -66, 156, 106, 12, k.b) + R(-68, -56, 136, 86, 6, k.p) + R(-56, -40, 60, 8, 4, k.d) + R(-56, -24, 100, 8, 4, k.c) +
                R(-56, -8, 80, 8, 4, k.a) + R(-56, 8, 42, 8, 4, k.m) + PA("M-96 52 H96 Q96 76 76 76 H-76 Q-96 76 -96 52Z", k.a) + R(-20, 52, 40, 7, 3, k.c)
        },
        "calculator" to { k: V3K ->
            var o = R(-58, -84, 116, 168, 22, k.a) + R(-42, -68, 84, 40, 8, k.p) + R(-8, -58, 40, 8, 4, k.d)
            for (j in 0 until 4) for (i in 0 until 3) o += R(-42 + i * 30, -14 + j * 25, 24, 19, 8, if (i == 2 && j > 1) k.d else k.c)
            o
        },
        "telescope" to { k: V3K ->
            R(-80, -30, 130, 38, 12, k.a, tf("rotate(-24)")) + R(30, -66, 56, 46, 10, k.b, tf("rotate(-24)")) + R(-96, -12, 30, 30, 8, k.m, tf("rotate(-24)")) +
                S("M-8 8 L-44 86", 9, k.b) + S("M-4 8 L30 86", 9, k.b) + S("M-6 8 L-6 86", 9, k.m) + ST4(60, -78, 20, k.d) + C(-70, -70, 6, k.m)
        },
        "dna" to { k: V3K ->
            var a = ""
            var b = ""
            for (i in 0..20) {
                val y = -90.0 + i * 9
                val x = sin(i / 20.0 * PI * 3) * 52
                a += (if (i > 0) "L" else "M") + f(x) + " " + f(y)
                b += (if (i > 0) "L" else "M") + f(-x) + " " + f(y)
            }
            var r = ""
            var i = 1
            while (i < 20) {
                val y = -90.0 + i * 9
                val x = sin(i / 20.0 * PI * 3) * 52
                r += S("M" + f(x) + " " + f(y) + "L" + f(-x) + " " + f(y), 6, if (i % 4 == 1) k.d else k.c)
                i += 2
            }
            r + S(a, 12, k.a) + S(b, 12, k.b)
        },
        "bulb" to { k: V3K ->
            var o = ""
            for (i in 0 until 7) {
                val a = (-150 + i * 20) * PI / 180
                o += S("M" + f(cos(a) * 78) + " " + f(-14 + sin(a) * 78) + "L" + f(cos(a) * 96) + " " + f(-14 + sin(a) * 96), 8, k.d)
            }
            o + C(0, -14, 56, k.c) + PA("M-26 24 Q-30 50 -18 54 H18 Q30 50 26 24Z", k.c) + R(-22, 54, 44, 12, 5, k.b) + R(-16, 70, 32, 12, 6, k.a) +
                S("M-14 30 V-4 Q-14 -18 0 -18 Q14 -18 14 -4 V30", 7, k.d)
        },
        "pencil" to { k: V3K ->
            "<g transform=\"rotate(-35)\">" + R(-90, -22, 40, 44, 8, k.d) + R(-54, -22, 14, 44, 0, k.m) + R(-40, -22, 100, 44, 0, k.a) + R(-40, -22, 100, 10, 0, k.c) +
                PL(k.p, 60, -22, 100, 0, 60, 22) + PL(k.b, 84, -7, 100, 0, 84, 7) + "</g>"
        },
        "cap" to { k: V3K ->
            PA(RP(8, 0, -66, 96, -22, 0, 22, -96, -22), k.b) + PA("M-52 0 V44 Q0 80 52 44 V0 L0 22Z", k.a) + S("M70 -14 V40", 6, k.d) + C(70, 46, 11, k.d)
        },
        "trophy" to { k: V3K ->
            S("M-56 -52 Q-96 -52 -88 -14 Q-80 12 -46 14", 11, k.m) + S("M56 -52 Q96 -52 88 -14 Q80 12 46 14", 11, k.m) +
                PA("M-58 -66 H58 V-24 Q58 22 0 30 Q-58 22 -58 -24Z", k.d) + R(-9, 28, 18, 30, 0, k.a) + R(-38, 56, 76, 26, 8, k.b) + ST4(0, -28, 26, k.p)
        },
        "clock" to { k: V3K ->
            C(0, 0, 84, k.a) + C(0, 0, 66, k.p) + S("M0 0 V-42", 9, k.b) + S("M0 0 L30 18", 9, k.d) + C(0, 0, 8, k.b) + C(0, -52, 3, k.m) + C(0, 52, 3, k.m) +
                C(-52, 0, 3, k.m) + C(52, 0, 3, k.m) + R(-46, -98, 22, 14, 6, k.b, tf("rotate(-24 -35 -90)")) + R(24, -98, 22, 14, 6, k.b, tf("rotate(24 35 -90)"))
        },
        "notebook" to { k: V3K ->
            var o = R(-64, -88, 128, 176, 16, k.a) + R(-52, -76, 112, 152, 8, k.p)
            for (i in 0 until 6) o += R(-30, -52 + i * 22, if (i % 3 == 2) 60 else 78, 6, 3, if (i == 0) k.d else k.c)
            for (i in 0 until 6) o += C(-64, -66 + i * 26, 7, k.b)
            o
        },
        "rocket" to { k: V3K ->
            PA("M-30 20 L-62 60 L-30 54Z", k.d) + PA("M30 20 L62 60 L30 54Z", k.d) + PA("M0 -92 Q50 -50 38 40 H-38 Q-50 -50 0 -92Z", k.p) +
                PA("M0 -92 Q26 -72 36 -40 H-36 Q-26 -72 0 -92Z", k.b) + C(0, -10, 16, k.a) + C(0, -10, 8, k.c) + PA("M-20 44 Q0 100 20 44Z", k.d) + R(-38, 36, 76, 10, 5, k.m)
        }
    )
}
