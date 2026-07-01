package id.soundbreaker.studio.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.soundbreaker.studio.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackListItem(
    name: String,
    type: String,
    color: Color,
    volume: Float,
    isMuted: Boolean,
    isSolo: Boolean,
    isArmed: Boolean,
    isActive: Boolean,
    onMuteClick: () -> Unit,
    onSoloClick: () -> Unit,
    onRecordClick: () -> Unit,
    onSelect: () -> Unit = {},
    onDoubleClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(if (isActive) Color(0xFF1E1E2E) else Color.Transparent)
            .combinedClickable(
                onClick = onSelect,
                onDoubleClick = onDoubleClick,
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = type,
                color = TextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TrackControlButton("M", isMuted, AccentRed, onMuteClick)
            TrackControlButton("S", isSolo, AccentOrange, onSoloClick)
            TrackControlButton("R", isArmed, TransportRed, onRecordClick)
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .width(50.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(DarkBorderLight)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = volume)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AccentGreen)
            )
        }
    }
}

@Composable
private fun TrackControlButton(
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(if (isActive) activeColor else Color(0xFF1A1A1A))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (isActive) Color.White else TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
