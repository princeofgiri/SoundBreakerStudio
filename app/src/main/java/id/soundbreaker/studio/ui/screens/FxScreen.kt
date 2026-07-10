package id.soundbreaker.studio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.soundbreaker.studio.data.Effect
import id.soundbreaker.studio.data.EffectType
import id.soundbreaker.studio.data.Track
import id.soundbreaker.studio.ui.theme.*

@Composable
fun FxScreen(
    tracks: List<Track>,
    selectedTrackId: Int,
    onSelectTrack: (Int) -> Unit,
    onAddEffect: (Int, EffectType) -> Unit,
    onRemoveEffect: (Int, Int) -> Unit,
    onToggleEffect: (Int, Int) -> Unit,
    onSetParam: (Int, Int, String, Float) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(12.dp),
    ) {
        // Track selector
        Column(
            modifier = Modifier
                .width(140.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
                .padding(8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Tracks", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            tracks.forEach { track ->
                val hasActiveFx = track.effects.any { it.isEnabled }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .then(
                            if (hasActiveFx) Modifier.border(1.dp, AccentGreen.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            else Modifier
                        )
                        .background(if (track.id == selectedTrackId) AccentRed.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { onSelectTrack(track.id) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(track.color)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(track.name, color = if (track.id == selectedTrackId) TextPrimary else TextMuted, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Effect chain
        val selectedTrack = tracks.find { it.id == selectedTrackId }
        if (selectedTrack != null) {
            Column(
                modifier = Modifier
                    .width(200.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurface)
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Effect Chain", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(8.dp))

                selectedTrack.effects.forEach { fx ->
                    EffectChainItem(
                        fx = fx,
                        onToggle = { onToggleEffect(selectedTrack.id, fx.id) },
                        onRemove = { onRemoveEffect(selectedTrack.id, fx.id) },
                        onParamChange = { key, value -> onSetParam(selectedTrack.id, fx.id, key, value) },
                    )
                }

                // Add effect slot
                EffectAddSlot(onAddEffect = { type -> onAddEffect(selectedTrack.id, type) })
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Effect parameters detail
            var selectedFx by remember { mutableStateOf<Effect?>(null) }
            LaunchedEffect(selectedTrack.effects) {
                if (selectedFx != null) {
                    selectedFx = selectedTrack.effects.find { it.id == selectedFx?.id }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurface)
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                val displayFx = selectedFx ?: selectedTrack.effects.firstOrNull()
                if (displayFx != null) {
                    Text(displayFx.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    when (displayFx.name) {
                        "Compressor" -> {
                            EffectParamSlider("Threshold", displayFx.params["threshold"] ?: 0.5f, 0f, 1f, AccentRed) { v ->
                                onSetParam(selectedTrack.id, displayFx.id, "threshold", v)
                            }
                            EffectParamSlider("Ratio", displayFx.params["ratio"] ?: 4f, 1f, 20f, AccentOrange) { v ->
                                onSetParam(selectedTrack.id, displayFx.id, "ratio", v)
                            }
                        }
                        "Reverb" -> {
                            EffectParamSlider("Amount", displayFx.params["amount"] ?: 0.3f, 0f, 1f, AccentBlue) { v ->
                                onSetParam(selectedTrack.id, displayFx.id, "amount", v)
                            }
                            EffectParamSlider("Decay", displayFx.params["decay"] ?: 0.5f, 0f, 1f, AccentBlue) { v ->
                                onSetParam(selectedTrack.id, displayFx.id, "decay", v)
                            }
                        }
                        "Delay" -> {
                            EffectParamSlider("Time", displayFx.params["time"] ?: 0.3f, 0.01f, 1f, AccentGreen) { v ->
                                onSetParam(selectedTrack.id, displayFx.id, "time", v)
                            }
                            EffectParamSlider("Feedback", displayFx.params["feedback"] ?: 0.4f, 0f, 0.95f, AccentGreen) { v ->
                                onSetParam(selectedTrack.id, displayFx.id, "feedback", v)
                            }
                        }
                        "Chorus" -> {
                            EffectParamSlider("Depth", displayFx.params["depth"] ?: 0.3f, 0f, 1f, AccentOrange) { v ->
                                onSetParam(selectedTrack.id, displayFx.id, "depth", v)
                            }
                            EffectParamSlider("Rate", displayFx.params["rate"] ?: 0.5f, 0.1f, 5f, AccentOrange) { v ->
                                onSetParam(selectedTrack.id, displayFx.id, "rate", v)
                            }
                        }
                        "Distortion" -> {
                            EffectParamSlider("Drive", displayFx.params["drive"] ?: 0.3f, 0f, 1f, AccentRed) { v ->
                                onSetParam(selectedTrack.id, displayFx.id, "drive", v)
                            }
                            EffectParamSlider("Tone", displayFx.params["tone"] ?: 0.5f, 0f, 1f, AccentOrange) { v ->
                                onSetParam(selectedTrack.id, displayFx.id, "tone", v)
                            }
                        }
                        "Filter" -> {
                            EffectParamSlider("Cutoff", displayFx.params["cutoff"] ?: 0.8f, 0.01f, 1f, AccentBlue) { v ->
                                onSetParam(selectedTrack.id, displayFx.id, "cutoff", v)
                            }
                            EffectParamSlider("Resonance", displayFx.params["resonance"] ?: 0.3f, 0f, 1f, AccentBlue) { v ->
                                onSetParam(selectedTrack.id, displayFx.id, "resonance", v)
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Select an effect to edit", color = TextMuted, fontSize = 12.sp)
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Select a track", color = TextMuted, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun EffectChainItem(
    fx: Effect,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    onParamChange: (String, Float) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (fx.isEnabled) DarkSurfaceVariant else Color(0xFF111111))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Toggle switch
        Box(
            modifier = Modifier
                .size(width = 32.dp, height = 18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (fx.isEnabled) AccentGreen else DarkBorderLight)
                .clickable { onToggle() },
        ) {
            Box(
                modifier = Modifier
                    .offset(x = if (fx.isEnabled) 16.dp else 2.dp, y = 2.dp)
                    .size(14.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color.White)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = fx.name,
            color = if (fx.isEnabled) TextPrimary else TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = "×",
            color = AccentRed,
            fontSize = 16.sp,
            modifier = Modifier.clickable { onRemove() },
        )
    }
}

@Composable
private fun EffectAddSlot(onAddEffect: (EffectType) -> Unit) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(DarkBorder)
            .clickable { showMenu = !showMenu }
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text("+ Add Effect", color = TextMuted, fontSize = 12.sp)
    }

    if (showMenu) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF2A2A2A))
        ) {
            EffectType.entries.forEach { type ->
                Text(
                    text = type.displayName,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onAddEffect(type)
                            showMenu = false
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun EffectParamSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    color: Color,
    onValueChange: (Float) -> Unit,
) {
    val currentValue = rememberUpdatedState(onValueChange)
    val fraction = ((value - min) / (max - min)).coerceIn(0f, 1f)

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = TextSecondary, fontSize = 12.sp)
            Text(String.format("%.2f", value), color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.height(4.dp))
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
                                    currentValue.value(min + f * (max - min))
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.Center)
                    .clip(RoundedCornerShape(2.dp)).background(DarkBorder)
            )
            Box(
                modifier = Modifier.fillMaxWidth(fraction).height(4.dp).align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(2.dp)).background(color)
            )
            Box(
                modifier = Modifier.fillMaxWidth(fraction).height(18.dp).align(Alignment.CenterStart)
            ) {
                Box(
                    modifier = Modifier.align(Alignment.CenterEnd).size(10.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape).background(color)
                )
            }
        }
    }
}
