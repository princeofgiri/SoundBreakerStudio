package id.soundbreaker.studio.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
                            .background(if (eqEnabled) Color(0xFF00C853).copy(alpha = 0.2f) else Color(0xFF2A2A2A))
                            .clickable { eqEnabled = !eqEnabled },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("⏻", color = if (eqEnabled) Color(0xFF00C853) else TextMuted, fontSize = 18.sp)
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
                 EqCurveVisualization(eqBands = eqBands, enabled = eqEnabled)

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
private fun EqCurveVisualization(eqBands: List<Float>, enabled: Boolean) {
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
                val amplitude = (Math.sin(i * 0.3).toFloat() * 0.4f + 0.6f) * (h * 0.35f)
                drawRect(
                    color = Color(0xFF00C853).copy(alpha = 0.6f),
                    topLeft = Offset(x, centerY - amplitude),
                    size = Size(barWidth, amplitude * 2),
                )
            }

            // Draw EQ curve line (cyan)
            val eqPath = Path()
            val numPoints = 100
            for (i in 0 until numPoints) {
                val t = i.toFloat() / numPoints
                val x = t * w
                val bandPos = t * (eqBands.size - 1)
                val bandIdx = bandPos.toInt().coerceIn(0, eqBands.size - 2)
                val bandFrac = bandPos - bandIdx
                val g1 = eqBands.getOrElse(bandIdx) { 0f }
                val g2 = eqBands.getOrElse(bandIdx + 1) { 0f }
                val gain = g1 + (g2 - g1) * bandFrac
                val y = centerY - (gain / 12f) * (h * 0.35f)

                if (i == 0) eqPath.moveTo(x, y)
                else eqPath.lineTo(x, y)
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
