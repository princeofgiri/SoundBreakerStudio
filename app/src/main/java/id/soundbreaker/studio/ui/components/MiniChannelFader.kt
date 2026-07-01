package id.soundbreaker.studio.ui.components

import androidx.compose.foundation.background
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
fun MiniChannelFader(
    label: String,
    dbValue: String,
    volume: Float,
    color: Color,
    isMaster: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = if (isMaster) TextSecondary else TextMuted,
            fontSize = 9.sp,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .width(24.dp)
                .height(80.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A1A1A))
                .then(
                    if (isMaster) Modifier.background(
                        Color(0xFF1A1A1A),
                        RoundedCornerShape(12.dp)
                    )
                    else Modifier
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fraction = volume)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                color,
                                color.copy(alpha = 0.2f),
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-(volume * 80).dp + 74.dp))
                    .height(12.dp)
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color(0xFFEEEEEE), Color(0xFF999999))
                        )
                    ),
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = dbValue,
            color = TextMuted,
            fontSize = 9.sp,
        )

        Box(
            modifier = Modifier
                .width(24.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isMaster) AccentRed else color)
        )
    }
}
