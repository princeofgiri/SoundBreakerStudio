package id.soundbreaker.studio.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.soundbreaker.studio.data.AudioRegion
import id.soundbreaker.studio.ui.theme.*

@Composable
fun TimelineToolbar(
    hasSelectedRegion: Boolean = false,
    onDeleteSelected: () -> Unit = {},
    onCutSelected: () -> Unit = {},
    onNudgeLeft: () -> Unit = {},
    onNudgeRight: () -> Unit = {},
    hasTracks: Boolean = false,
    isInspectorVisible: Boolean = true,
    onToggleInspector: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(Color(0xFF161616))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tools = listOf("⬚", "✂", "🔗", "✎", "✕")
        tools.forEachIndexed { index, tool ->
            val isActive = index == 0
            val isDelete = index == 4
            val isCut = index == 1
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        when {
                            isActive -> AccentRed
                            isDelete && hasSelectedRegion -> AccentRed.copy(alpha = 0.6f)
                            isCut && hasSelectedRegion -> AccentOrange.copy(alpha = 0.6f)
                            else -> Color(0xFF222222)
                        }
                    )
                    .clickable {
                        when {
                            isDelete && hasSelectedRegion -> onDeleteSelected()
                            isCut && hasSelectedRegion -> onCutSelected()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(tool, color = if (isActive) Color.White else TextSecondary, fontSize = 14.sp)
            }
            if (index == 3) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(DarkBorder)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }

        // Nudge left/right buttons (only when region selected)
        if (hasSelectedRegion) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF222222))
                    .clickable { onNudgeLeft() },
                contentAlignment = Alignment.Center,
            ) {
                Text("◀", color = AccentOrange, fontSize = 12.sp)
            }
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF222222))
                    .clickable { onNudgeRight() },
                contentAlignment = Alignment.Center,
            ) {
                Text("▶", color = AccentOrange, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF222222))
                    .clickable { },
                contentAlignment = Alignment.Center,
            ) {
                Text("−", color = TextSecondary, fontSize = 14.sp)
            }
            Text("100%", color = TextMuted, fontSize = 11.sp)
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF222222))
                    .clickable { },
                contentAlignment = Alignment.Center,
            ) {
                Text("+", color = TextSecondary, fontSize = 14.sp)
            }

            // Inspector toggle (only show when tracks exist)
            if (hasTracks) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isInspectorVisible) AccentBlue.copy(alpha = 0.3f) else Color(0xFF222222))
                        .clickable { onToggleInspector() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("I", color = if (isInspectorVisible) AccentBlue else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun TimelineRuler(
    totalBars: Int = 200,
    currentBar: Float = 1f,
    barWidthDp: androidx.compose.ui.unit.Dp = 40.dp,
    onBarTap: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val barWidthPx = with(density) { barWidthDp.toPx() }

    Box(
        modifier = modifier
            .width(barWidthDp * totalBars)
            .height(24.dp)
            .background(Color(0xFF161616))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val tappedBar = (offset.x / barWidthPx) + 1f
                    onBarTap?.invoke(tappedBar.coerceIn(1f, totalBars.toFloat()))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val tappedBar = (change.position.x / barWidthPx) + 1f
                    onBarTap?.invoke(tappedBar.coerceIn(1f, totalBars.toFloat()))
                }
            },
    ) {
        for (bar in 1..totalBars) {
            Box(
                modifier = Modifier
                    .offset(x = barWidthDp * (bar - 1))
                    .width(barWidthDp)
                    .fillMaxHeight(),
            ) {
                if (bar % 4 == 1) {
                    Text(
                        text = "$bar",
                        color = TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 2.dp, bottom = 2.dp),
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(if (bar % 4 == 1) Color(0xFF3A3A3A) else Color(0xFF222222))
                )
            }
        }

        // Playhead indicator
        val playheadX = barWidthDp * (currentBar - 1f)
        Box(
            modifier = Modifier
                .offset(x = playheadX)
                .width(2.dp)
                .height(10.dp)
                .align(Alignment.BottomStart)
                .background(AccentRed)
        )
    }
}

@Composable
fun TrackLane(
    regions: List<AudioRegion>,
    color: Color,
    isEven: Boolean,
    totalBars: Int = 200,
    currentBar: Float = 1f,
    selectedRegionId: Int? = null,
    touchedRegionId: Int? = null,
    onRegionTap: (Int) -> Unit = {},
    onRegionDrag: (Int, Float) -> Unit = { _, _ -> },
    onRegionDragStart: (Int) -> Unit = {},
    onRegionDragEnd: () -> Unit = {},
    onBackgroundTap: (Float) -> Unit = {},
    barWidthDp: androidx.compose.ui.unit.Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val barWidthPx = with(density) { barWidthDp.toPx() }

    Box(
        modifier = modifier
            .width(barWidthDp * totalBars)
            .height(72.dp)
            .clipToBounds()
            .background(if (isEven) Color(0xFF0F0F0F) else Color(0xFF111111))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val bar = (offset.x / barWidthPx) + 1f
                        onBackgroundTap(bar)
                    }
                )
            },
    ) {
        regions.forEach { region ->
            val isSel = region.id == selectedRegionId
            val isTouched = region.id == touchedRegionId
            androidx.compose.runtime.key(region.id) {
                AudioRegionItem(
                    region = region,
                    color = color,
                    isSel = isSel,
                    isTouched = isTouched,
                    barWidthPx = barWidthPx,
                    density = density,
                    onRegionTap = onRegionTap,
                    onRegionDrag = onRegionDrag,
                    onRegionDragStart = onRegionDragStart,
                    onRegionDragEnd = onRegionDragEnd
                )
            }
        }

        val playheadX = barWidthDp * (currentBar - 1f)
        Box(
            modifier = Modifier
                .offset(x = playheadX)
                .width(2.dp)
                .fillMaxHeight()
                .background(AccentRed)
        )
    }
}

@Composable
fun TimelineScrollBar(
    scrollPx: Int,
    onScrollPxChange: (Int) -> Unit,
    maxScrollPx: Int,
    totalWidthDp: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val totalWidthPx = with(density) { totalWidthDp.toPx() }

    BoxWithConstraints(modifier = modifier) {
        val viewportWidth = constraints.maxWidth.toFloat()
        if (totalWidthPx <= viewportWidth || maxScrollPx <= 0) return@BoxWithConstraints

        val thumbRatio = (viewportWidth / totalWidthPx).coerceIn(0.05f, 1f)
        val thumbWidth = thumbRatio * viewportWidth
        val maxThumbX = viewportWidth - thumbWidth
        val scrollFraction = scrollPx.toFloat() / maxScrollPx

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF1A1A1A))
        )

        val currentScrollPx by rememberUpdatedState(scrollPx)
        val currentMaxScrollPx by rememberUpdatedState(maxScrollPx)
        val currentMaxThumbX by rememberUpdatedState(maxThumbX)
        val currentOnScrollPxChange by rememberUpdatedState(onScrollPxChange)

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(with(density) { thumbWidth.toDp() })
                .offset(x = with(density) { (scrollFraction * maxThumbX).toDp() })
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF555555))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val delta = (dragAmount.x / currentMaxThumbX * currentMaxScrollPx).toInt()
                        currentOnScrollPxChange((currentScrollPx + delta).coerceIn(0, currentMaxScrollPx))
                    }
                }
        )
    }
}

@Composable
private fun AudioRegionItem(
    region: AudioRegion,
    color: Color,
    isSel: Boolean,
    isTouched: Boolean,
    barWidthPx: Float,
    density: androidx.compose.ui.unit.Density,
    onRegionTap: (Int) -> Unit,
    onRegionDrag: (Int, Float) -> Unit,
    onRegionDragStart: (Int) -> Unit,
    onRegionDragEnd: () -> Unit,
) {
    val currentStartBar by rememberUpdatedState(region.startBar)
    val currentOnRegionDrag by rememberUpdatedState(onRegionDrag)
    val currentOnRegionTap by rememberUpdatedState(onRegionTap)
    val currentOnRegionDragStart by rememberUpdatedState(onRegionDragStart)
    val currentOnRegionDragEnd by rememberUpdatedState(onRegionDragEnd)

    val startPx = (region.startBar - 1f) * barWidthPx
    val widthPx = region.widthBars * barWidthPx

    Box(
        modifier = Modifier
            .offset(x = with(density) { startPx.toDp() }, y = 6.dp)
            .width(with(density) { widthPx.toDp() })
            .height(60.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    isTouched -> Color(0xFF666666).copy(alpha = 0.5f)
                    isSel -> color.copy(alpha = 0.3f)
                    else -> color.copy(alpha = 0.15f)
                }
            )
            .then(
                if (isSel && !isTouched) {
                    Modifier.border(2.dp, color, RoundedCornerShape(6.dp))
                } else Modifier
            )
            .pointerInput(region.id) {
                detectTapGestures(
                    onTap = {
                        Log.d("TimelineGesture", "Tap region.id = ${region.id}")
                        currentOnRegionTap(region.id)
                    }
                )
            }
            .pointerInput(region.id) {
                var dragStartBar = 0f
                var totalDrag = 0f
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        Log.d("TimelineGesture", "DragStart region.id = ${region.id}, currentStartBar = $currentStartBar")
                        currentOnRegionTap(region.id)
                        currentOnRegionDragStart(region.id)
                        dragStartBar = currentStartBar
                        totalDrag = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDrag += dragAmount.x
                        val newStartBar = dragStartBar + totalDrag / barWidthPx
                        Log.d("TimelineGesture", "Dragging region.id = ${region.id}, dragAmount.x = ${dragAmount.x}, totalDrag = $totalDrag, newStartBar = $newStartBar")
                        currentOnRegionDrag(region.id, newStartBar)
                    },
                    onDragEnd = {
                        Log.d("TimelineGesture", "DragEnd region.id = ${region.id}")
                        currentOnRegionDragEnd()
                    },
                    onDragCancel = {
                        Log.d("TimelineGesture", "DragCancel region.id = ${region.id}")
                        currentOnRegionDragEnd()
                    }
                )
            }
    ) {
        val waveform = region.waveform
        if (waveform != null && waveform.isNotEmpty()) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                val waveWidth = size.width
                val waveHeight = size.height
                val centerY = waveHeight / 2
                val waveBarWidthPx = waveWidth / waveform.size
                val halfBarHeight = waveHeight / 2

                for (i in waveform.indices) {
                    val x = i * waveBarWidthPx
                    val barHeight = waveform[i] * halfBarHeight

                    drawRect(
                        color = color.copy(alpha = 0.5f),
                        topLeft = Offset(x, centerY - barHeight),
                        size = Size(waveBarWidthPx.coerceAtLeast(2f), barHeight * 2),
                    )
                }
            }
        }

        Text(
            text = region.name,
            color = if (isSel || isTouched) Color.White else color.copy(alpha = 0.8f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(start = 8.dp, top = 4.dp),
            overflow = TextOverflow.Ellipsis
        )
    }
}
