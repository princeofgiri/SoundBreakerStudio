package id.soundbreaker.studio.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.soundbreaker.studio.data.MasterEqPresets
import id.soundbreaker.studio.ui.theme.*

@Composable
fun MasterEqScreen(
    eqBands: List<Float>,
    currentPreset: String,
    onBandChange: (Int, Float) -> Unit,
    onPresetSelect: (String) -> Unit,
) {
    var eqEnabled by remember { mutableStateOf(true) }
    var isAnimationPlaying by remember { mutableStateOf(true) }

    val quickPresets = listOf("Flat", "Bass", "Vocal", "Bright", "V-Shape")

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D1117))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            // Card container
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF161B22))
                    .padding(16.dp),
            ) {
                // Header: power + title + presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Power button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (eqEnabled) Color(0xFF00C853).copy(alpha = 0.2f) else Color(0xFF21262D))
                            .clickable { eqEnabled = !eqEnabled },
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(modifier = Modifier.size(16.dp)) {
                            val color = if (eqEnabled) Color(0xFF00C853) else Color(0xFF8B949E)
                            drawArc(
                                color = color,
                                startAngle = -240f,
                                sweepAngle = 300f,
                                useCenter = false,
                                style = Stroke(width = 2.dp.toPx())
                            )
                            drawLine(
                                color = color,
                                start = Offset(size.width / 2f, 0f),
                                end = Offset(size.width / 2f, size.height * 0.6f),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text("PARAMETRIC EQ · 10-BAND", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("CH 01 — MASTER BUS", color = TextMuted, fontSize = 10.sp)
                    }

                    Spacer(modifier = Modifier.weight(1f))

                     // Preset chips
                     Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                         quickPresets.forEach { name ->
                             val isSelected = name == currentPreset
                             Box(
                                 modifier = Modifier
                                     .clip(RoundedCornerShape(16.dp))
                                     .background(
                                         when {
                                             !eqEnabled -> Color(0xFF161B22)
                                             isSelected -> Color(0xFF00C853).copy(alpha = 0.3f)
                                             else -> Color(0xFF21262D)
                                         }
                                     )
                                     .then(
                                         if (eqEnabled) Modifier.clickable { onPresetSelect(name) }
                                         else Modifier
                                     )
                                     .padding(horizontal = 12.dp, vertical = 6.dp),
                             ) {
                                 Text(
                                     text = name,
                                     color = when {
                                         !eqEnabled -> TextMuted
                                         isSelected -> Color(0xFF00C853)
                                         else -> TextSecondary
                                     },
                                     fontSize = 10.sp,
                                     fontWeight = FontWeight.Medium,
                                 )
                             }
                         }
                     }

                     Spacer(modifier = Modifier.width(8.dp))

                     // Reset button
                     Box(
                         modifier = Modifier
                             .size(32.dp)
                             .clip(CircleShape)
                             .background(if (eqEnabled) Color(0xFF21262D) else Color(0xFF161B22))
                             .then(
                                 if (eqEnabled) Modifier.clickable { onPresetSelect("Flat") }
                                 else Modifier
                             ),
                         contentAlignment = Alignment.Center,
                     ) {
                         Text("↺", color = if (eqEnabled) TextSecondary else TextMuted, fontSize = 16.sp)
                     }
                 }

                 Spacer(modifier = Modifier.height(8.dp))

                 // Waveform + EQ Curve visualization
                 EqCurveVisualization(
                     eqBands = eqBands,
                     enabled = eqEnabled,
                     isAnimationPlaying = isAnimationPlaying,
                     onPlayPauseToggle = { isAnimationPlaying = !isAnimationPlaying }
                 )

                 Spacer(modifier = Modifier.height(12.dp))

                 // EQ Band Sliders
                 Row(
                     modifier = Modifier
                         .fillMaxWidth()
                         .weight(1f),
                     horizontalArrangement = Arrangement.SpaceEvenly,
                     verticalAlignment = Alignment.Top,
                 ) {
                     MasterEqPresets.bandLabels.forEachIndexed { index, label ->
                         val gain = eqBands.getOrElse(index) { 0f }
                         EqBandSlider(
                             label = label,
                             gain = gain,
                             onGainChange = { onBandChange(index, it) },
                             enabled = eqEnabled,
                         )
                     }
                 }

                 Spacer(modifier = Modifier.height(8.dp))

                 // Bottom info bar
                 Row(
                     modifier = Modifier.fillMaxWidth(),
                     horizontalArrangement = Arrangement.SpaceBetween,
                     verticalAlignment = Alignment.CenterVertically,
                 ) {
                     Text(
                         "GAIN RANGE ±12 dB · DOUBLE-CLICK FADER = 0 dB",
                         color = TextMuted,
                         fontSize = 9.sp,
                     )
                     Text(
                         "PRESET: $currentPreset",
                         color = when {
                             !eqEnabled -> TextMuted
                             currentPreset == "Custom" -> TextMuted
                             else -> Color(0xFF00C853)
                         },
                         fontSize = 9.sp,
                         fontWeight = FontWeight.Bold,
                     )
                 }
            }
        }
    }
}

@Composable
private fun EqCurveVisualization(
    eqBands: List<Float>,
    enabled: Boolean,
    isAnimationPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val currentPhase = if (isAnimationPlaying && enabled) phase else 0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0D1117))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val centerY = h / 2

            // Draw grid lines
            for (i in 0..4) {
                val y = (i / 4f) * h
                drawLine(
                    color = Color(0xFF21262D),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f,
                )
            }

            if (!enabled) return@Canvas

            // Draw waveform bars (green)
            val numBars = 60
            for (i in 0 until numBars) {
                val x = (i.toFloat() / numBars) * w
                val barWidth = w / numBars * 0.7f
                val amplitude = (Math.sin(i * 0.2 + currentPhase).toFloat() * 0.4f + 0.6f) * (h * 0.35f)
                drawRect(
                    color = Color(0xFF00C853).copy(alpha = 0.6f),
                    topLeft = Offset(x, centerY - amplitude),
                    size = Size(barWidth, amplitude * 2),
                )
            }

            // Draw EQ curve line (cyan) as a smooth Catmull-Rom spline
            val points = ArrayList<Offset>()
            for (j in 0 until eqBands.size) {
                val x = (j.toFloat() / (eqBands.size - 1)) * w
                val gain = eqBands.getOrElse(j) { 0f }
                val y = centerY - (gain / 12f) * (h * 0.35f)
                points.add(Offset(x, y))
            }

            val eqPath = Path()
            eqPath.moveTo(points[0].x, points[0].y)

            for (j in 0 until points.size - 1) {
                val p0 = points[if (j == 0) 0 else j - 1]
                val p1 = points[j]
                val p2 = points[j + 1]
                val p3 = points[if (j + 2 >= points.size) points.size - 1 else j + 2]

                val steps = 20
                for (step in 1..steps) {
                    val t = step.toFloat() / steps
                    val t2 = t * t
                    val t3 = t2 * t

                    val x = 0.5f * (
                        (2f * p1.x) +
                        (-p0.x + p2.x) * t +
                        (2f * p0.x - 5f * p1.x + 4f * p2.x - p3.x) * t2 +
                        (-p0.x + 3f * p1.x - 3f * p2.x + p3.x) * t3
                    )

                    val y = 0.5f * (
                        (2f * p1.y) +
                        (-p0.y + p2.y) * t +
                        (2f * p0.y - 5f * p1.y + 4f * p2.y - p3.y) * t2 +
                        (-p0.y + 3f * p1.y - 3f * p2.y + p3.y) * t3
                    )

                    eqPath.lineTo(x, y)
                }
            }

            drawPath(
                path = eqPath,
                color = Color(0xFF00BCD4),
                style = Stroke(width = 2.dp.toPx()),
            )
        }

        // Labels
        Text("WAVEFORM + EQ CURVE", color = TextMuted, fontSize = 8.sp, modifier = Modifier.padding(8.dp))
        Text("+12 dB", color = TextMuted, fontSize = 8.sp, modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp, top = 8.dp))

        // Play/Pause button at bottom-left of visualization box
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (enabled) Color(0xFF161B22) else Color(0xFF0D1117))
                .border(
                    width = 1.dp,
                    color = if (enabled) Color(0xFF00C853).copy(alpha = 0.5f) else Color(0xFF30363D),
                    shape = CircleShape
                )
                .then(
                    if (enabled) Modifier.clickable { onPlayPauseToggle() }
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isAnimationPlaying) "⏸" else "▶",
                color = if (enabled) Color(0xFF00C853) else TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun EqBandSlider(
    label: String,
    gain: Float,
    onGainChange: (Float) -> Unit,
    enabled: Boolean = true,
) {
    val currentOnGain by rememberUpdatedState(onGainChange)
    val fraction = ((gain + 12f) / 24f).coerceIn(0f, 1f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxHeight(),
    ) {
        // Gain value
        Text(
            text = if (kotlin.math.abs(gain) < 0.05f) "0.0" else String.format("%.1f", gain),
            color = when {
                !enabled -> TextMuted
                kotlin.math.abs(gain) > 0.01f -> Color(0xFF00C853)
                else -> TextMuted
            },
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Slider track container
        BoxWithConstraints(
            modifier = Modifier
                .width(24.dp)
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF21262D))
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onDoubleTap = {
                            currentOnGain(0f)
                        },
                        onTap = { offset ->
                            val trackHeightPx = size.height.toFloat()
                            val initialFraction = (1f - offset.y / trackHeightPx).coerceIn(0f, 1f)
                            currentOnGain(initialFraction * 24f - 12f)
                        }
                    )
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val trackHeightPx = size.height.toFloat()
                            val newFraction = (1f - change.position.y / trackHeightPx).coerceIn(0f, 1f)
                            currentOnGain(newFraction * 24f - 12f)
                        }
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            val trackHeightDp = maxHeight
            val thumbHeightDp = 8.dp
            val usableHeightDp = trackHeightDp - thumbHeightDp

            // Zero line
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(1.dp)
                    .background(Color(0xFF30363D))
            )

            // Green fill bar from center (fraction = 0.5) to gain
            if (kotlin.math.abs(gain) > 0.01f && enabled) {
                val barFraction = kotlin.math.abs(fraction - 0.5f)
                val barHeightDp = usableHeightDp * barFraction
                val yOffsetDp = if (fraction > 0.5f) {
                    // Positive gain: starts from center and goes UP
                    usableHeightDp / 2f - barHeightDp + thumbHeightDp / 2f
                } else {
                    // Negative gain: starts from center and goes DOWN
                    usableHeightDp / 2f + thumbHeightDp / 2f
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .width(4.dp)
                        .height(barHeightDp)
                        .offset(y = yOffsetDp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF00C853))
                )
            }

            // Thumb
            val thumbYOffsetDp = usableHeightDp * (1f - fraction)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = thumbYOffsetDp)
                    .width(18.dp)
                    .height(thumbHeightDp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (enabled) Color(0xFF6E7681) else Color(0xFF484F58))
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Frequency label
        Text(
            text = label,
            color = if (enabled) TextSecondary else TextMuted,
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
