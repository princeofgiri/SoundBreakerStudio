package id.soundbreaker.studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
    eqLow: String,
    eqMid: String,
    eqHigh: String,
    effects: List<Pair<String, Boolean>>,
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(DarkSurface)
    ) {
        // Track Inspector
        InspectorSection("Track Inspector") {
            InspectorRow("Name", trackName)
            InspectorRow("Type", trackType)
            InspectorRow("Sample Rate", "${sampleRate} Hz")
            Spacer(modifier = Modifier.height(12.dp))
            InspectorRow("Volume", "${(volume * 100).toInt()}%")
            InspectorSlider(volume)
            Spacer(modifier = Modifier.height(12.dp))
            InspectorRow("Pan", when {
                pan < 0.45f -> "L${((0.5f - pan) * 200).toInt()}"
                pan > 0.55f -> "R${((pan - 0.5f) * 200).toInt()}"
                else -> "C"
            })
            InspectorSlider(pan)
        }

        // EQ
        InspectorSection("EQ") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                EqKnob("Low", eqLow, AccentRed)
                EqKnob("Mid", eqMid, AccentOrange)
                EqKnob("High", eqHigh, AccentBlue)
            }
        }

        // Effects Chain
        InspectorSection("Effects Chain") {
            effects.forEach { (name, enabled) ->
                EffectSlot(name, enabled)
            }
            EffectSlot("+ Add Effect", false, isAddButton = true)
        }

        Spacer(modifier = Modifier.weight(1f))

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
private fun InspectorSlider(value: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(DarkBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = value)
                .clip(RoundedCornerShape(3.dp))
                .background(AccentRed)
        )
    }
}

@Composable
private fun EqKnob(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.sweepGradient(
                        colors = listOf(color, DarkBorderLight, color, DarkBorderLight, color),
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceVariant)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, color = TextMuted, fontSize = 10.sp)
        Text(value, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
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


