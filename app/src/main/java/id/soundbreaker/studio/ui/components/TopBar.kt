package id.soundbreaker.studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import id.soundbreaker.studio.ui.theme.*

@Composable
fun TopBar(
    projectName: String,
    activeTab: String,
    onTabChange: (String) -> Unit,
    onNew: () -> Unit = {},
    onOpen: () -> Unit = {},
    onSave: () -> Unit = {},
    onExport: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(DarkSurfaceVariant)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "STUDIO",
            color = AccentRed,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )

        Spacer(modifier = Modifier.width(16.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF252525))
                .clickable { }
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(projectName, color = TextSecondary, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Edit", "Mix", "FX", "Master EQ").forEach { tab ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (tab == activeTab) AccentRed else Color.Transparent)
                        .clickable { onTabChange(tab) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = tab,
                        color = if (tab == activeTab) Color.White else TextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("New", "Open", "Save", "Export").forEach { action ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            when (action) {
                                "New" -> onNew()
                                "Open" -> onOpen()
                                "Save" -> onSave()
                                "Export" -> onExport()
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(action, color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}
