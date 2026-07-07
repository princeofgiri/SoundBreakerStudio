package id.soundbreaker.studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
                awaitEachGesture {
                    val down = awaitPointerEvent(PointerEventPass.Initial)
                    val firstChange = down.changes.firstOrNull()
                    android.util.Log.e("SB", "Ruler: Initial pass, pressed=${firstChange?.pressed}, consumed=${firstChange?.isConsumed}")
                    if (firstChange != null && firstChange.pressed) {
                        firstChange.consume()
                        android.util.Log.e("SB", "Ruler: consumed=true")
                        val tappedBar = (firstChange.position.x / barWidthPx) + 1f
                        if (onBarTap != null) {
                            onBarTap(tappedBar.coerceIn(1f, totalBars.toFloat()))
                        }
                    }
                    // Keep consuming drag events so parent scroll doesn't start
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
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
    onRegionTap: (Int) -> Unit = {},
    onRegionDrag: (Int, Float) -> Unit = { _, _ -> },
    onBackgroundTap: (Float) -> Unit = {},
    barWidthDp: androidx.compose.ui.unit.Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val barWidthPx = with(density) { barWidthDp.toPx() }

    val regionTopPx = with(density) { 6.dp.toPx() }
    val regionHeightPx = with(density) { 60.dp.toPx() }
    val regionRadiusPx = with(density) { 6.dp.toPx() }

    Box(
        modifier = modifier
            .width(barWidthDp * totalBars)
            .height(72.dp)
            .clipToBounds()
            .background(if (isEven) Color(0xFF0F0F0F) else Color(0xFF111111))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val downEvent = awaitPointerEvent(PointerEventPass.Initial)
                    val firstChange = downEvent.changes.firstOrNull() ?: return@awaitEachGesture
                    if (!firstChange.pressed) return@awaitEachGesture
                    firstChange.consume()

                    // Check if touch hits any region
                    val hitRegion = regions.firstOrNull { region ->
                        val startPx = (region.startBar - 1f) * barWidthPx
                        val endPx = startPx + region.widthBars * barWidthPx
                        firstChange.position.x in startPx..endPx &&
                        firstChange.position.y in regionTopPx..(regionTopPx + regionHeightPx)
                    }

                    if (hitRegion != null) {
                        onRegionTap(hitRegion.id)
                        // Handle drag
                        var prevX = firstChange.position.x
                        var accumulatedDx = 0f
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull() ?: break
                            if (change.pressed) {
                                change.consume()
                                val dx = change.position.x - prevX
                                accumulatedDx += dx
                                val beatWidth = barWidthPx / 4f
                                val beats = (accumulatedDx / beatWidth).toInt()
                                if (beats != 0) {
                                    onRegionDrag(hitRegion.id, hitRegion.startBar + beats * 0.25f)
                                    accumulatedDx -= beats * beatWidth
                                }
                                prevX = change.position.x
                            }
                        } while (event.changes.any { it.pressed })
                    } else {
                        // Background tap — playback
                        val tappedBar = (firstChange.position.x / barWidthPx) + 1f
                        onBackgroundTap(tappedBar.coerceIn(1f, totalBars.toFloat()))
                    }
                }
            },
    ) {

        // Canvas for drawing regions + waveform
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            regions.forEach { region ->
                val startPx = (region.startBar - 1f) * barWidthPx
                val widthPx = region.widthBars * barWidthPx
                val left = startPx
                val top = regionTopPx
                val isSelected = region.id == selectedRegionId

                // Region background
                drawRoundRect(
                    color = if (isSelected) color.copy(alpha = 0.3f) else color.copy(alpha = 0.15f),
                    topLeft = Offset(left, top),
                    size = Size(widthPx, regionHeightPx),
                    cornerRadius = CornerRadius(regionRadiusPx),
                )

                // Selection border
                if (isSelected) {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(left, top),
                        size = Size(widthPx, regionHeightPx),
                        cornerRadius = CornerRadius(regionRadiusPx),
                        style = Stroke(width = with(density) { 2.dp.toPx() }),
                    )
                }

                // Draw waveform (symmetric filled envelope)
                val waveform = region.waveform
                if (waveform != null && waveform.isNotEmpty()) {
                    val waveWidth = widthPx - 16.dp.toPx()
                    val waveHeight = regionHeightPx - 16.dp.toPx()
                    val waveStartX = left + 8.dp.toPx()
                    val waveStartY = top + 8.dp.toPx()
                    val centerY = waveStartY + waveHeight / 2
                    val barWidthPx = waveWidth / waveform.size
                    val halfBarHeight = waveHeight / 2

                    for (i in waveform.indices) {
                        val x = waveStartX + i * barWidthPx
                        val barHeight = waveform[i] * halfBarHeight

                        // Filled symmetric bar from center
                        drawRect(
                            color = color.copy(alpha = 0.5f),
                            topLeft = Offset(x, centerY - barHeight),
                            size = Size(barWidthPx.coerceAtLeast(2f), barHeight * 2),
                        )
                    }
                }

                // Region name text
                val textLayoutResult = textMeasurer.measure(
                    text = region.name,
                    style = TextStyle(
                        color = if (isSelected) Color.White else color.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = androidx.compose.ui.geometry.Offset(left + with(density) { 8.dp.toPx() }, top + with(density) { 4.dp.toPx() }),
                )
            }
        }

        // Playhead line as Box (same offset method as ruler)
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
    scrollState: androidx.compose.foundation.ScrollState,
    totalWidthDp: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val totalWidthPx = with(density) { totalWidthDp.toPx() }
    var scrollPosition by remember { mutableIntStateOf(0) }

    // Sync: scrollState -> scrollPosition (when external scroll happens)
    LaunchedEffect(Unit) {
        snapshotFlow { scrollState.value }.collect { scrollPosition = it }
    }

    // Sync: scrollPosition -> scrollState (when scrollbar is dragged)
    LaunchedEffect(scrollPosition) {
        scrollState.scrollTo(scrollPosition)
    }

    BoxWithConstraints(modifier = modifier) {
        val viewportWidth = constraints.maxWidth.toFloat()
        val maxValue = scrollState.maxValue
        if (totalWidthPx <= viewportWidth || maxValue <= 0) return@BoxWithConstraints

        val thumbRatio = (viewportWidth / totalWidthPx).coerceIn(0.05f, 1f)
        val thumbWidth = thumbRatio * viewportWidth
        val maxThumbX = viewportWidth - thumbWidth
        val scrollFraction = if (maxValue > 0) scrollPosition.toFloat() / maxValue else 0f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF1A1A1A))
        )

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
                        val delta = (dragAmount.x / maxThumbX * maxValue).toInt()
                        scrollPosition = (scrollPosition + delta).coerceIn(0, maxValue)
                    }
                }
        )
    }
}

