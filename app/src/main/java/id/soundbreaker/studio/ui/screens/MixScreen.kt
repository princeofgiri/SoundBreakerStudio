package id.soundbreaker.studio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.soundbreaker.studio.data.Track
import id.soundbreaker.studio.ui.theme.*

@Composable
fun MixScreen(
    tracks: List<Track>,
    selectedTrackId: Int,
    masterVolume: Float,
    masterPan: Float,
    trackAmplitudes: List<Float> = emptyList(),
    onSelectTrack: (Int) -> Unit,
    onMuteToggle: (Int) -> Unit,
    onSoloToggle: (Int) -> Unit,
    onVolumeChange: (Int, Float) -> Unit,
    onPanChange: (Int, Float) -> Unit,
    onMasterVolumeChange: (Float) -> Unit,
    onMasterPanChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom,
    ) {
        tracks.forEachIndexed { index, track ->
            MixChannelStrip(
                track = track,
                amplitude = trackAmplitudes.getOrElse(index) { 0f },
                isSelected = track.id == selectedTrackId,
                onSelect = { onSelectTrack(track.id) },
                onMute = { onMuteToggle(track.id) },
                onSolo = { onSoloToggle(track.id) },
                onVolume = { onVolumeChange(track.id, it) },
                onPan = { onPanChange(track.id, it) },
            )
        }
        // Master strip
        MixMasterStrip(
            volume = masterVolume,
            pan = masterPan,
            onVolumeChange = onMasterVolumeChange,
            onPanChange = onMasterPanChange,
        )
    }
}

@Composable
private fun MixChannelStrip(
    track: Track,
    amplitude: Float = 0f,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onMute: () -> Unit,
    onSolo: () -> Unit,
    onVolume: (Float) -> Unit,
    onPan: (Float) -> Unit,
) {
    val currentOnVolume = rememberUpdatedState(onVolume)
    val currentOnPan = rememberUpdatedState(onPan)
    val faderHeight = 200.dp
    val density = LocalDensity.current

    Column(
        modifier = Modifier
            .width(48.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color(0xFF1E1E2E) else DarkSurface)
            .clickable { onSelect() }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Track name
        Text(
            text = track.name.take(4),
            color = TextPrimary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        // Color indicator
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(track.color)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Level meter (simulated with volume)
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(faderHeight)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fraction = amplitude.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                AccentGreen,
                                AccentOrange,
                                AccentRed,
                            )
                        )
                    )
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Volume fader
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(faderHeight)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1A1A1A))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            for (change in event.changes) {
                                if (change.pressed || change.previousPressed) {
                                    val fraction = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                                    currentOnVolume.value(fraction)
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // Center line
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(DarkBorderLight)
            )
            // Thumb
            val density = LocalDensity.current
            val thumbOffsetPx = (0.5f - track.volume) * with(density) { faderHeight.toPx() }
            val thumbOffset = with(density) { thumbOffsetPx.toDp() }
            Box(
                modifier = Modifier
                    .offset(y = thumbOffset)
                    .width(20.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFEEEEEE))
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Volume dB label
        Text(
            text = "${((track.volume - 0.5f) * 20).toInt().let { if (it >= 0) "+$it" else "$it" }}",
            color = TextMuted,
            fontSize = 9.sp,
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Pan knob (vertical)
        Text("PAN", color = TextMuted, fontSize = 7.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(80.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF1A1A1A))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            for (change in event.changes) {
                                if (change.pressed || change.previousPressed) {
                                    val fraction = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                                    currentOnPan.value(fraction)
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // Center tick
            Box(
                modifier = Modifier
                    .width(14.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(AccentOrange)
            )
            // Thumb
            val panThumbPx = (0.5f - track.pan) * with(density) { 80.dp.toPx() }
            val panThumb = with(density) { panThumbPx.toDp() }
            Box(
                modifier = Modifier
                    .offset(y = panThumb)
                    .width(16.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFEEEEEE))
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Pan label
        Text(
            text = when {
                track.pan < 0.45f -> "L${((0.5f - track.pan) * 200).toInt()}"
                track.pan > 0.55f -> "R${((track.pan - 0.5f) * 200).toInt()}"
                else -> "C"
            },
            color = TextMuted,
            fontSize = 8.sp,
        )

        Spacer(modifier = Modifier.height(6.dp))

        // M / S buttons
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (track.isMuted) AccentRed else Color(0xFF1A1A1A))
                    .clickable { onMute() },
                contentAlignment = Alignment.Center,
            ) {
                Text("M", color = if (track.isMuted) Color.White else TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (track.isSolo) AccentOrange else Color(0xFF1A1A1A))
                    .clickable { onSolo() },
                contentAlignment = Alignment.Center,
            ) {
                Text("S", color = if (track.isSolo) Color.White else TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MixMasterStrip(
    volume: Float,
    pan: Float,
    onVolumeChange: (Float) -> Unit,
    onPanChange: (Float) -> Unit,
) {
    val currentOnVolume = rememberUpdatedState(onVolumeChange)
    val currentOnPan = rememberUpdatedState(onPanChange)
    val faderHeight = 200.dp
    val density = LocalDensity.current

    Column(
        modifier = Modifier
            .width(56.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "MASTER",
            color = AccentRed,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        // Master fader
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(faderHeight)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1A1A1A))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            for (change in event.changes) {
                                if (change.pressed || change.previousPressed) {
                                    val fraction = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                                    currentOnVolume.value(fraction)
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(DarkBorderLight)
            )
            val thumbOffsetPx = (0.5f - volume) * with(density) { faderHeight.toPx() }
            val thumbOffset = with(density) { thumbOffsetPx.toDp() }
            Box(
                modifier = Modifier
                    .offset(y = thumbOffset)
                    .width(24.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFFEEEEEE))
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "${((volume - 0.5f) * 20).toInt().let { if (it >= 0) "+$it" else "$it" }}",
            color = TextMuted,
            fontSize = 9.sp,
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Pan
        Text("PAN", color = TextMuted, fontSize = 7.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(80.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF1A1A1A))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            for (change in event.changes) {
                                if (change.pressed || change.previousPressed) {
                                    val fraction = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                                    currentOnPan.value(fraction)
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(AccentOrange)
            )
            val panThumbPx = (0.5f - pan) * with(density) { 80.dp.toPx() }
            val panThumb = with(density) { panThumbPx.toDp() }
            Box(
                modifier = Modifier
                    .offset(y = panThumb)
                    .width(20.dp)
                    .height(7.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFEEEEEE))
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = when {
                pan < 0.45f -> "L${((0.5f - pan) * 200).toInt()}"
                pan > 0.55f -> "R${((pan - 0.5f) * 200).toInt()}"
                else -> "C"
            },
            color = TextMuted,
            fontSize = 8.sp,
        )
    }
}
