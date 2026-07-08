package id.soundbreaker.studio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    var showPresets by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Preset selector
        Column(
            modifier = Modifier
                .width(120.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
                .padding(8.dp),
        ) {
            Text("Presets", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))

            MasterEqPresets.presets.keys.forEach { name ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (name == currentPreset) AccentRed.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { onPresetSelect(name) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = name,
                        color = if (name == currentPreset) AccentRed else TextSecondary,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        // EQ bands
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
                .padding(16.dp),
        ) {
            Text("Master EQ", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(currentPreset, color = AccentRed, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(16.dp))

            // EQ bands - vertical sliders
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MasterEqPresets.bandLabels.forEachIndexed { index, label ->
                    val gain = eqBands.getOrElse(index) { 0f }
                    EqBandSlider(
                        label = label,
                        gain = gain,
                        onGainChange = { onBandChange(index, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EqBandSlider(
    label: String,
    gain: Float,
    onGainChange: (Float) -> Unit,
) {
    val currentOnGain = rememberUpdatedState(onGainChange)
    val faderHeight = 200.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    val fraction = ((gain + 12f) / 24f).coerceIn(0f, 1f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Gain value
        Text(
            text = if (kotlin.math.abs(gain) < 0.5f) "0" else "${gain.toInt().let { if (it > 0) "+$it" else "$it" }}",
            color = if (kotlin.math.abs(gain) > 0.01f) AccentOrange else TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Slider track
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(faderHeight)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF1A1A1A))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitPointerEvent()
                        val change = down.changes.firstOrNull() ?: return@awaitEachGesture
                        if (change.pressed) {
                            val newFraction = (1f - change.position.y / with(density) { faderHeight.toPx() }).coerceIn(0f, 1f)
                            currentOnGain.value(newFraction * 24f - 12f)
                            change.consume()
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // Zero line (center)
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(2.dp)
                    .background(DarkBorderLight)
            )

            // Fill from center to current gain
            if (kotlin.math.abs(gain) > 0.5f) {
                val barHeight = kotlin.math.abs(fraction - 0.5f) * 200f
                val barStart = if (gain > 0) 0.5f else fraction
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(with(density) { barHeight.toDp() })
                        .offset(y = with(density) {
                            val centerY = 100f
                            val offsetPx = if (gain > 0) centerY - barHeight else centerY
                            offsetPx.toDp()
                        })
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = if (gain > 0) listOf(AccentOrange, AccentRed) else listOf(AccentBlue, AccentGreen)
                            )
                        )
                )
            }

            // Thumb
            Box(
                modifier = Modifier
                    .offset(y = with(density) {
                        ((0.5f - fraction) * 200f).toDp()
                    })
                    .width(20.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFEEEEEE))
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Frequency label
        Text(label, color = TextMuted, fontSize = 9.sp)
    }
}
