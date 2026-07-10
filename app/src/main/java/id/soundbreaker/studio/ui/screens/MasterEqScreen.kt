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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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
    playbackAmplitude: Float = 0f,
    isPlaying: Boolean = false,
    eqEnabled: Boolean = true,
    onEnabledChange: (Boolean) -> Unit = {},
    customPresets: Map<String, List<Float>> = emptyMap(),
    onSavePreset: (String, List<Float>) -> Unit = { _, _ -> },
    onDeletePreset: (String) -> Unit = {},
) {
    var isAnimationPlaying by remember { mutableStateOf(true) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }

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
                            .clickable { onEnabledChange(!eqEnabled) },
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
                        Text("BREAKER EQ · 10-BAND", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Dropdown Preset Selector
                    Box {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (eqEnabled) Color(0xFF21262D) else Color(0xFF161B22))
                                .border(1.dp, Color(0xFF30363D), RoundedCornerShape(8.dp))
                                .then(
                                    if (eqEnabled) Modifier.clickable { dropdownExpanded = true }
                                    else Modifier
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Preset: $currentPreset",
                                    color = if (eqEnabled) TextPrimary else TextMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "▼",
                                    color = if (eqEnabled) TextSecondary else TextMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = dropdownExpanded && eqEnabled,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier.background(Color(0xFF161B22)).border(1.dp, Color(0xFF30363D), RoundedCornerShape(8.dp))
                        ) {
                            val builtIn = listOf("Flat", "Bass", "Vocal", "Bright", "V-Shape", "Pop", "Rock", "Country", "Punk", "Jazz", "Classical", "Electronic", "Hip-Hop", "Acoustic", "Warm")
                             
                            Text(
                                text = "BUILT-IN PRESETS",
                                color = TextMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                            builtIn.forEach { name ->
                                DropdownMenuItem(
                                    text = { Text(name, color = if (name == currentPreset) Color(0xFF00C853) else TextPrimary, fontSize = 13.sp) },
                                    onClick = {
                                        onPresetSelect(name)
                                        dropdownExpanded = false
                                    }
                                )
                            }

                            if (customPresets.isNotEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF30363D)))
                                Text(
                                    text = "CUSTOM PRESETS",
                                    color = TextMuted,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                                customPresets.keys.forEach { name ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(name, color = if (name == currentPreset) Color(0xFF00C853) else TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                                Text(
                                                    text = "✕",
                                                    color = AccentRed,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier
                                                        .clickable {
                                                            onDeletePreset(name)
                                                        }
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        },
                                        onClick = {
                                            onPresetSelect(name)
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Save Preset Button
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (eqEnabled) Color(0xFF21262D) else Color(0xFF161B22))
                            .border(1.dp, Color(0xFF30363D), CircleShape)
                            .then(
                                if (eqEnabled) Modifier.clickable { showSaveDialog = true }
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", color = if (eqEnabled) Color(0xFF00C853) else TextMuted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Reset button
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (eqEnabled) Color(0xFF21262D) else Color(0xFF161B22))
                            .border(1.dp, Color(0xFF30363D), CircleShape)
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
                      onPlayPauseToggle = { isAnimationPlaying = !isAnimationPlaying },
                      playbackAmplitude = playbackAmplitude,
                      isPlaying = isPlaying,
                      modifier = Modifier
                          .fillMaxWidth()
                          .weight(0.4f)
                  )

                 Spacer(modifier = Modifier.height(12.dp))

                  // EQ Band Sliders container (dark, bordered card!)
                  Box(
                      modifier = Modifier
                          .fillMaxWidth()
                          .weight(0.6f)
                          .clip(RoundedCornerShape(8.dp))
                          .background(Color(0xFF0D1117))
                          .border(1.dp, Color(0xFF30363D), RoundedCornerShape(8.dp))
                          .padding(horizontal = 16.dp, vertical = 12.dp)
                  ) {
                      Row(
                          modifier = Modifier.fillMaxSize(),
                          horizontalArrangement = Arrangement.SpaceEvenly,
                          verticalAlignment = Alignment.CenterVertically,
                      ) {
                          MasterEqPresets.bandLabels.forEachIndexed { index, label ->
                              Box(
                                  modifier = Modifier
                                      .weight(1f)
                                      .fillMaxHeight(),
                                  contentAlignment = Alignment.Center
                              ) {
                                  val gain = eqBands.getOrElse(index) { 0f }
                                  EqBandSlider(
                                      label = label,
                                      gain = gain,
                                      onGainChange = { onBandChange(index, it) },
                                      enabled = eqEnabled,
                                  )
                              }
                          }
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

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = Color(0xFF1A1A1A),
            titleContentColor = TextPrimary,
            title = { Text("Save Preset Baru", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column {
                    Text("Masukkan nama untuk preset custom Anda:", color = TextSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPresetName,
                        onValueChange = { newPresetName = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = Color(0xFF00C853),
                            unfocusedBorderColor = DarkBorderLight,
                            cursorColor = Color(0xFF00C853)
                        ),
                        placeholder = { Text("Nama Preset", color = TextMuted) }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPresetName.isNotBlank()) {
                            onSavePreset(newPresetName.trim(), eqBands)
                            newPresetName = ""
                            showSaveDialog = false
                        }
                    }
                ) {
                    Text("Simpan", color = Color(0xFF00C853), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Batal", color = TextMuted)
                }
            }
        )
    }
}

@Composable
private fun EqCurveVisualization(
    eqBands: List<Float>,
    enabled: Boolean,
    isAnimationPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    playbackAmplitude: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
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

    val currentPhase = if (isAnimationPlaying && enabled && isPlaying) phase else 0f

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0D1117))
            .border(1.dp, Color(0xFF30363D), RoundedCornerShape(8.dp))
    ) {
    key(eqBands) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val centerY = h / 2

            // Draw grid lines (horizontal)
            for (i in 0..4) {
                val y = (i / 4f) * h
                drawLine(
                    color = Color(0xFF21262D),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f,
                )
            }

            // Draw grid lines (vertical, aligned with each slider!)
            val p = 16.dp.toPx() // horizontal padding to match faders
            val wActive = w - 2 * p
            val c = wActive / 10f

            for (j in 0 until 10) {
                val x = p + (j + 0.5f) * c
                drawLine(
                    color = Color(0xFF21262D),
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 1f,
                )
            }

            // Draw waveform bars (green)
            val numBars = 180
            val barWidth = w / numBars * 0.45f
            val rawAmp = if (isAnimationPlaying && isPlaying) playbackAmplitude else 0f
            val ampFactor = if (isAnimationPlaying && isPlaying) (rawAmp.coerceIn(0f, 1f) * 0.8f + 0.2f) else 0f

            for (i in 0 until numBars) {
                val x = (i.toFloat() / numBars) * w
                
                // Pseudo-random wave formula that changes over time (phase)
                val wave1 = Math.sin(i * 0.15 + currentPhase * 1.5).toFloat()
                val wave2 = Math.cos(i * 0.35 - currentPhase * 0.8).toFloat()
                val wave3 = Math.sin(i * 0.08 + currentPhase * 2.2).toFloat()
                
                // Combine waves and normalize to [0, 1] range
                val baseHeight = ((wave1 * 0.4f + wave2 * 0.3f + wave3 * 0.3f) * 0.5f + 0.5f)
                val amplitude = baseHeight * (h * 0.42f) * ampFactor

                if (amplitude > 0.01f) {
                    // Top half (bright green)
                    drawRect(
                        color = Color(0xFF00E676),
                        topLeft = Offset(x, centerY - amplitude),
                        size = Size(barWidth, amplitude),
                    )
                    // Bottom half (translucent green reflection)
                    drawRect(
                        color = Color(0xFF00E676).copy(alpha = 0.2f),
                        topLeft = Offset(x, centerY),
                        size = Size(barWidth, amplitude * 0.8f),
                    )
                }
            }

            // Draw EQ curve line (cyan) as a smooth Catmull-Rom spline
            val points = ArrayList<Offset>()
            for (j in 0 until eqBands.size) {
                val x = p + (j + 0.5f) * c
                val gain = eqBands.getOrElse(j) { 0f }
                val y = centerY - (gain / 12f) * (h * 0.35f)
                points.add(Offset(x, y))
            }

            // Create extended points to draw the spline to left and right edges
            val splinePoints = ArrayList<Offset>()
            splinePoints.add(Offset(0f, points[0].y))
            splinePoints.addAll(points)
            splinePoints.add(Offset(w, points.last().y))

            val eqPath = Path()
            eqPath.moveTo(splinePoints[0].x, splinePoints[0].y)

            for (j in 0 until splinePoints.size - 1) {
                val p0 = splinePoints[if (j == 0) 0 else j - 1]
                val p1 = splinePoints[j]
                val p2 = splinePoints[j + 1]
                val p3 = splinePoints[if (j + 2 >= splinePoints.size) splinePoints.size - 1 else j + 2]

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

        // Slider track container (width 32.dp to fit the thumb without clipping)
        BoxWithConstraints(
            modifier = Modifier
                .width(32.dp)
                .weight(1f)
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
            val thumbHeightDp = 16.dp
            val usableHeightDp = trackHeightDp - thumbHeightDp

            // Track background (vertical bar, width 12.dp)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF21262D))
            )

            // Zero line indicator
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(1.dp)
                    .background(Color(0xFF30363D))
            )

            // Gradient fill bar from center (fraction = 0.5) to gain
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

                val density = LocalDensity.current
                val usableHeightPx = with(density) { usableHeightDp.toPx() }
                val thumbHeightPx = with(density) { thumbHeightDp.toPx() }
                val yOffsetPx = with(density) { yOffsetDp.toPx() }

                Canvas(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .width(6.dp)
                        .height(barHeightDp)
                        .offset(y = yOffsetDp)
                ) {
                    val startY = - (yOffsetPx - thumbHeightPx / 2f)
                    val endY = usableHeightPx - (yOffsetPx - thumbHeightPx / 2f)
                    val gradientBrush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0xFFFF1744), // Top (+12dB) -> Red
                            0.25f to Color(0xFFFFD600), // +6dB -> Yellow
                            0.5f to Color(0xFF00C853),  // Center (0dB) -> Green
                            0.75f to Color(0xFFFFD600), // -6dB -> Yellow
                            1.0f to Color(0xFFFF1744)  // Bottom (-12dB) -> Red
                        ),
                        startY = startY,
                        endY = endY
                    )
                    drawRoundRect(
                        brush = gradientBrush,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                }
            }

            // Thumb (horizontal capsule with border and middle green line)
            val thumbYOffsetDp = usableHeightDp * (1f - fraction)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = thumbYOffsetDp)
                    .width(32.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (enabled) Color(0xFF161B22) else Color(0xFF0D1117))
                    .border(
                        width = 1.dp,
                        color = if (enabled) Color(0xFF30363D) else Color(0xFF21262D),
                        shape = RoundedCornerShape(4.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .height(2.dp)
                        .background(if (enabled) Color(0xFF00C853) else Color(0xFF484F58))
                )
            }
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
