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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.soundbreaker.studio.ui.theme.*

@Composable
fun TransportBar(
    timeDisplay: String,
    bpm: Int,
    timeSignature: String,
    isPlaying: Boolean,
    isRecording: Boolean,
    isLooping: Boolean,
    isClickOn: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onRecord: () -> Unit,
    onLoopToggle: () -> Unit,
    onClickToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(Color(0xFF111111))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Time Display
        Column(
            modifier = Modifier.width(200.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = timeDisplay,
                color = AccentRed,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                letterSpacing = 2.sp,
            )
            Text(
                text = "Bars : Beats : Ticks",
                color = TextMuted,
                fontSize = 10.sp,
            )
        }

        // Transport Controls
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransportButton("⏮", false, TextSecondary, Modifier.size(44.dp))
                TransportButton("⏪", false, TextSecondary, Modifier.size(44.dp))
                TransportButton("▶", isPlaying, AccentGreen, Modifier.size(52.dp)) { onPlay() }
                TransportButton("●", isRecording, TransportRed, Modifier.size(52.dp)) { onRecord() }
                TransportButton("■", false, TextSecondary, Modifier.size(52.dp)) { onStop() }
                TransportButton("⏩", false, TextSecondary, Modifier.size(44.dp))
                TransportButton("⏭", false, TextSecondary, Modifier.size(44.dp))
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransportInfo("BPM", "$bpm")
                TransportInfo("Time Sig", timeSignature)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ToggleSwitch(isLooping, AccentGreen, onLoopToggle)
                    Text("Loop", color = TextMuted, fontSize = 12.sp)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Click", color = TextMuted, fontSize = 12.sp)
                    ToggleSwitch(isClickOn, AccentOrange, onClickToggle)
                }
            }
        }
    }
}

@Composable
private fun TransportButton(
    symbol: String,
    isActive: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (isActive) activeColor else Color(0xFF1A1A1A))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            color = if (isActive) {
                if (activeColor == TransportRed) Color.White
                else if (activeColor == AccentGreen) Color.Black
                else Color.White
            } else TextSecondary,
            fontSize = 18.sp,
        )
    }
}

@Composable
private fun TransportInfo(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = TextMuted, fontSize = 12.sp)
        Text(value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ToggleSwitch(
    isOn: Boolean,
    activeColor: Color,
    onToggle: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 32.dp, height = 18.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (isOn) activeColor else DarkBorderLight)
            .clickable { onToggle() },
    ) {
        Box(
            modifier = Modifier
                .offset(x = if (isOn) 16.dp else 2.dp, y = 2.dp)
                .size(14.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}
