package com.neb.ians.ui.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neb.ians.R
import com.neb.ians.ui.theme.Poppins
import com.neb.ians.ui.theme.nebAnimationsReduced
import com.neb.ians.ui.theme.nebSpatialSpec
import com.neb.ians.util.NebSystemBars
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    NebSystemBars(darkIcons = !dark)

    val reduced = nebAnimationsReduced()
    val spatial = nebSpatialSpec<Float>()

    val mark = remember { Animatable(0f) }
    val letters = remember { List(SPLASH_WORD.length) { Animatable(0f) } }
    val exit = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (reduced) {
            mark.snapTo(1f)
            letters.forEach { it.snapTo(1f) }
            delay(120)
        } else {
            launch { mark.animateTo(1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)) }
            delay(180)
            letters.forEachIndexed { i, letter ->
                launch {
                    delay(i * 28L)
                    letter.animateTo(1f, spatial)
                }
            }
            delay(360)
            exit.animateTo(1f, spring(Spring.DampingRatioNoBouncy, 650f, visibilityThreshold = 0.002f))
        }
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.graphicsLayer {
                val s = 1f + 0.05f * exit.value
                scaleX = s
                scaleY = s
                translationY = -20.dp.toPx() * exit.value
                alpha = 1f - exit.value
            }
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(224.dp)
                    .graphicsLayer {
                        val t = mark.value
                        val s = 0.62f + 0.38f * t
                        scaleX = s
                        scaleY = s
                        rotationZ = (1f - t) * -14f
                        translationY = (1f - t) * -32.dp.toPx()
                        alpha = (t * 2.5f).coerceIn(0f, 1f)
                    }
            )
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.offset(y = (-18).dp)) {
                SPLASH_WORD.forEachIndexed { i, ch ->
                    Box(Modifier.clipToBounds()) {
                        Text(
                            text = ch.toString(),
                            fontFamily = Poppins,
                            fontWeight = if (i == 0) FontWeight.ExtraBold else FontWeight.SemiBold,
                            fontSize = 36.sp,
                            color = if (i == 0) scheme.primary else scheme.onSurface,
                            maxLines = 1,
                            modifier = Modifier.graphicsLayer {
                                val t = letters[i].value
                                translationY = (1f - t) * size.height * 1.1f
                            }
                        )
                    }
                }
            }
        }
    }
}

private const val SPLASH_WORD = "Note"
