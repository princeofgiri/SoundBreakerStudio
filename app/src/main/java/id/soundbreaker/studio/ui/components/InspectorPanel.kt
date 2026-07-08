package id.soundbreaker.studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.soundbreaker.studio.ui.theme.*

@Composable
fun InspectorPanel(
    trackName: String,
    trackType: String,
    volume: Float,
    pan: Float,
    sampleRate: Int = 44100,
    channels: Int = 2,
    bitDepth: Int = 16,
    eqLow: String,
    eqMid: String,
    eqHigh: String,
    eqLowValue: Float = 0f,
    eqMidValue: Float = 0f,
    eqHighValue: Float = 0f,
    onEqChange: (Float, Float, Float) -> Unit = { _, _, _ -> },
    effects: List<Pair<String, Boolean>>,
    inputSource: String = "Mic",
    availableInputs: List<String> = listOf("Mic"),
    onInputSourceChange: (String) -> Unit = {},
    onDelete: () -> Unit = {},
    onVolumeChange: (Float) -> Unit = {},
    onPanChange: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(DarkSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
        // Track Inspector
        InspectorSection("Track Inspector") {
            InspectorRow("Name", trackName)
            InspectorRow("Type", trackType)
            InspectorRow("Sample Rate", "${sampleRate} Hz")
            InspectorRow("Bit Depth", "${bitDepth}-bit")
            Spacer(modifier = Modifier.height(8.dp))
            // Input Source selector
            InspectorRow("Input", inputSource)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                availableInputs.take(3).forEach { input ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (inputSource == input) AccentBlue.copy(alpha = 0.3f) else Color(0xFF1A1A1A))
                            .clickable { onInputSourceChange(input) }
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = input.take(6),
                            color = if (inputSource == input) AccentBlue else TextMuted,
                            fontSize = 9.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (availableInputs.size > 3) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    availableInputs.drop(3).take(3).forEach { input ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (inputSource == input) AccentBlue.copy(alpha = 0.3f) else Color(0xFF1A1A1A))
                                .clickable { onInputSourceChange(input) }
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = input.take(6),
                                color = if (inputSource == input) AccentBlue else TextMuted,
                                fontSize = 9.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            InspectorRow("Volume", "${(volume * 100).toInt()}%")
            InspectorSlider(value = volume, onValueChange = onVolumeChange)
            Spacer(modifier = Modifier.height(12.dp))
            InspectorRow("Pan", when {
                pan < 0.45f -> "L${((0.5f - pan) * 200).toInt()}"
                pan > 0.55f -> "R${((pan - 0.5f) * 200).toInt()}"
                else -> "C"
            })
            PanSlider(pan = pan, onPanChange = onPanChange)
        }

        // EQ
        InspectorSection("EQ") {
            EqSlider("Low", eqLow, eqLowValue, AccentRed) { onEqChange(it, eqMidValue, eqHighValue) }
            EqSlider("Mid", eqMid, eqMidValue, AccentOrange) { onEqChange(eqLowValue, it, eqHighValue) }
            EqSlider("High", eqHigh, eqHighValue, AccentBlue) { onEqChange(eqLowValue, eqMidValue, it) }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Delete Track Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AccentRed.copy(alpha = 0.15f))
                .clickable { onDelete() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Delete Track",
                color = AccentRed,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun InspectorSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp)
    ) {
        Text(
            text = title,
            color = TextMuted,
            fontSize = 11.sp,
            letterSpacing = 0.5.sp,
        )
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun InspectorRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = TextSecondary, fontSize = 12.sp)
        Text(value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun InspectorSlider(value: Float, onValueChange: (Float) -> Unit = {}) {
    val currentOnValueChange = rememberUpdatedState(onValueChange)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
            .clip(RoundedCornerShape(3.dp))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        for (change in event.changes) {
                            if (change.pressed || change.previousPressed) {
                                val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                currentOnValueChange.value(fraction)
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(3.dp))
                .background(DarkBorder)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction = value)
                .height(6.dp)
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(3.dp))
                .background(AccentRed)
        )
    }
}

@Composable
private fun EqSlider(label: String, valueText: String, dbValue: Float, color: Color, onValueChange: (Float) -> Unit) {
    val currentOnValueChange = rememberUpdatedState(onValueChange)
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = TextSecondary, fontSize = 12.sp)
            Text(valueText, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.height(4.dp))
        // Slider track: fill from 0dB position
        val fraction = (dbValue + 12f) / 24f
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(RoundedCornerShape(3.dp))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            for (change in event.changes) {
                                if (change.pressed || change.previousPressed) {
                                    val f = (change.position.x / size.width).coerceIn(0f, 1f)
                                    currentOnValueChange.value(f * 24f - 12f)
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(2.dp))
                    .background(DarkBorder)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = fraction)
                    .height(4.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
            // Indicator thumb
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = fraction)
                    .height(18.dp)
                    .align(Alignment.CenterStart)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }
    }
}

@Composable
private fun PanSlider(pan: Float, onPanChange: (Float) -> Unit) {
    val currentOnPanChange = rememberUpdatedState(onPanChange)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        for (change in event.changes) {
                            if (change.pressed || change.previousPressed) {
                                val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                currentOnPanChange.value(fraction)
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // Background track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(2.dp))
                .background(DarkBorder)
        )
        // Center tick mark
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(10.dp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(1.dp))
                .background(DarkBorderLight)
        )
        // Thumb
        val density = LocalDensity.current
        val trackWidthPx = with(density) { maxWidth.toPx() }
        val thumbX = with(density) { (pan * trackWidthPx).toDp() }
        Box(
            modifier = Modifier
                .offset(x = thumbX - 5.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(AccentOrange)
        )
    }
}

@Composable
private fun EffectSlot(name: String, isEnabled: Boolean, isAddButton: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isAddButton) Color.Transparent else DarkSurfaceVariant)
            .then(
                if (isAddButton) Modifier
                else Modifier.padding(horizontal = 0.dp)
            )
            .clickable { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(DarkBorder),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isAddButton) "+" else "🎛",
                fontSize = 12.sp,
            )
        }

        Text(
            text = name,
            color = if (isAddButton) TextMuted else TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )

        if (!isAddButton) {
            Box(
                modifier = Modifier
                    .size(width = 32.dp, height = 18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isEnabled) AccentGreen else DarkBorderLight)
                    .clickable { },
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = if (isEnabled) 16.dp else 2.dp, y = 2.dp)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
    }
}


