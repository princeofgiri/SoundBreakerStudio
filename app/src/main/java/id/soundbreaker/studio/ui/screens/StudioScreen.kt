package id.soundbreaker.studio.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.soundbreaker.studio.MainActivity
import id.soundbreaker.studio.data.*
import id.soundbreaker.studio.ui.components.*
import id.soundbreaker.studio.ui.theme.*
import kotlinx.coroutines.launch
import id.soundbreaker.studio.viewmodel.StudioViewModel

val RULER_HEIGHT = 24.dp
val TOOLBAR_HEIGHT = 40.dp

private fun formatEqDb(value: Float): String = if (value > 0) "+${value.toInt()} dB" else "${value.toInt()} dB"

@Composable
fun StudioScreen(viewModel: StudioViewModel) {
    val project by viewModel.project.collectAsState()
    val selectedTrackId by viewModel.selectedTrackId.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val message by viewModel.message.collectAsState()
    val selectedRegionId by viewModel.selectedRegionId.collectAsState()
    val isInspectorVisible by viewModel.isInspectorVisible.collectAsState()
    val masterVolume by viewModel.masterVolume.collectAsState()
    val masterPan by viewModel.masterPan.collectAsState()
    val trackAmplitudes by viewModel.trackAmplitudes.collectAsState()
    val availableInputs by viewModel.availableInputs.collectAsState()
    val availableOutputs by viewModel.availableOutputs.collectAsState()
    val outputDevice by viewModel.outputDevice.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()
    val context = LocalContext.current
    val density = LocalDensity.current

    var renamingTrackId by remember { mutableStateOf<Int?>(null) }
    var renameText by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteTrackId by remember { mutableStateOf<Int?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveNameText by remember { mutableStateOf(project.name) }
    var showBpmDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showTimeSigDialog by remember { mutableStateOf(false) }
    var exportFileName by remember { mutableStateOf("") }
    var exportFolderUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var exportFolderPath by remember { mutableStateOf("/sdcard/Music") }
    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            exportFolderUri = it
            // Try to resolve readable path
            val path = it.path ?: ""
            exportFolderPath = if (path.contains("/primary:")) {
                "/storage/emulated/0/" + path.substringAfter("/primary:")
            } else path.ifEmpty { "/sdcard/Music" }
        }
    }
    var bpmText by remember { mutableStateOf(project.bpm.toString()) }
    LaunchedEffect(project.bpm) { bpmText = project.bpm.toString() }
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val barWidth = 40.dp
    val barWidthPx = with(density) { barWidth.toPx() }

    // Auto-scroll to follow playhead (always, including navigation)
    LaunchedEffect(project.playheadPosition) {
        val playheadPx = (project.playheadPosition - 1f) * barWidthPx
        val scrollOffset = horizontalScrollState.value.toFloat()

        // Near right edge or past visible area: scroll to center playhead
        if (playheadPx > scrollOffset + barWidthPx * 20) {
            val target = (playheadPx - barWidthPx * 5).toInt().coerceAtLeast(0)
            horizontalScrollState.scrollTo(target)
        }
        // Near left edge
        if (playheadPx < scrollOffset + barWidthPx && scrollOffset > 0f) {
            val target = (playheadPx - barWidthPx * 2).toInt().coerceAtLeast(0)
            horizontalScrollState.scrollTo(target)
        }
    }

    LaunchedEffect(message) {
        message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); viewModel.clearMessage() }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importAudio(it) }
    }
    val openProjectLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.openProject(it) }
    }
    val exportWavLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { _ ->
        viewModel.exportWav()
    }

    var touchedRegionId by remember { mutableStateOf<Int?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(DarkBackground).statusBarsPadding()) {
        TopBar(
            projectName = project.name,
            activeTab = activeTab,
            onTabChange = { viewModel.setActiveTab(it) },
            onNew = { viewModel.newProject() },
            onOpen = { openProjectLauncher.launch(arrayOf("application/json", "*/*")) },
            onSave = { saveNameText = project.name; showSaveDialog = true },
            onExport = { exportFileName = "${project.name}_export"; showExportDialog = true },
        )

        if (isExporting) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                LinearProgressIndicator(
                    progress = { exportProgress },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = AccentGreen,
                    trackColor = DarkSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text("Exporting... ${(exportProgress * 100).toInt()}%", color = TextMuted, fontSize = 10.sp)
            }
        }

        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (activeTab == "Mix") {
                MixScreen(
                    tracks = project.tracks,
                    selectedTrackId = selectedTrackId,
                    masterVolume = masterVolume,
                    masterPan = masterPan,
                    trackAmplitudes = trackAmplitudes,
                    onSelectTrack = { viewModel.selectTrack(it) },
                    onMuteToggle = { viewModel.toggleMute(it) },
                    onSoloToggle = { viewModel.toggleSolo(it) },
                    onVolumeChange = { id, vol -> viewModel.setTrackVolume(id, vol) },
                    onPanChange = { id, pan -> viewModel.setTrackPan(id, pan) },
                    onMasterVolumeChange = { viewModel.setMasterVolume(it) },
                    onMasterPanChange = { viewModel.setMasterPan(it) },
                    outputDevice = outputDevice,
                    availableOutputs = availableOutputs,
                    onOutputDeviceChange = { viewModel.setOutputDevice(it) },
                )
            } else if (activeTab == "FX") {
                FxScreen(
                    tracks = project.tracks,
                    selectedTrackId = selectedTrackId,
                    onSelectTrack = { viewModel.selectTrack(it) },
                    onAddEffect = { trackId, type -> viewModel.addEffect(trackId, type) },
                    onRemoveEffect = { trackId, fxId -> viewModel.removeEffect(trackId, fxId) },
                    onToggleEffect = { trackId, fxId -> viewModel.toggleEffect(trackId, fxId) },
                    onSetParam = { trackId, fxId, key, value -> viewModel.setEffectParam(trackId, fxId, key, value) },
                )
            } else if (activeTab == "Master EQ") {
                val masterAmp = trackAmplitudes.maxOrNull() ?: 0f
                MasterEqScreen(
                    eqBands = project.masterEq,
                    currentPreset = project.masterEqPreset,
                    onBandChange = { index, gain -> viewModel.setMasterEqBand(index, gain) },
                    onPresetSelect = { viewModel.setMasterEqPreset(it) },
                    playbackAmplitude = masterAmp,
                    isPlaying = isPlaying && !isPaused,
                    eqEnabled = project.masterEqEnabled,
                    onEnabledChange = { viewModel.setMasterEqEnabled(it) },
                    customPresets = project.customPresets,
                    onSavePreset = { name, bands -> viewModel.saveCustomPreset(name, bands) },
                    onDeletePreset = { viewModel.deleteCustomPreset(it) },
                )
            } else {
            // Track List
            Column(modifier = Modifier.width(280.dp).fillMaxHeight().background(DarkSurface)) {
                PanelHeader("Tracks", onAdd = { viewModel.addTrack() })
                Box(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(verticalScrollState)) {
                    Column {
                        Spacer(modifier = Modifier.height(RULER_HEIGHT))
                        project.tracks.forEach { track ->
                            TrackListItem(
                                name = track.name, type = track.type.label, color = track.color,
                                volume = track.volume, isMuted = track.isMuted, isSolo = track.isSolo,
                                isArmed = track.isArmed, isActive = track.id == selectedTrackId,
                                onMuteClick = { viewModel.toggleMute(track.id) },
                                onSoloClick = { viewModel.toggleSolo(track.id) },
                                onRecordClick = { viewModel.toggleArm(track.id) },
                                onSelect = { viewModel.selectTrack(track.id) },
                                onDoubleClick = { renameText = track.name; renamingTrackId = track.id },
                                onDelete = { deleteTrackId = track.id; showDeleteDialog = true },
                                modifier = Modifier,
                            )
                        }
                    }
                }
            }

            // Timeline
            Column(modifier = Modifier.weight(1f).fillMaxHeight().background(DarkBackground)) {
                TimelineToolbar(
                    hasSelectedRegion = selectedRegionId != null,
                    onDeleteSelected = { viewModel.deleteSelectedRegion() },
                    onCutSelected = { viewModel.cutRegion() },
                    onNudgeLeft = { viewModel.nudgeRegionLeft() },
                    onNudgeRight = { viewModel.nudgeRegionRight() },
                    hasTracks = project.tracks.isNotEmpty(),
                    isInspectorVisible = isInspectorVisible,
                    onToggleInspector = { viewModel.toggleInspector() },
                )
                if (project.tracks.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth().background(DarkBackground))
                } else {
                        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                          BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            // Scrollable content
                            Column(modifier = Modifier
                                .fillMaxHeight()
                                .verticalScroll(verticalScrollState)
                                .horizontalScroll(horizontalScrollState)
                            ) {
                                TimelineRuler(totalBars = project.totalBars, currentBar = project.playheadPosition, barWidthDp = barWidth, onBarTap = { bar ->
                                    if (isPlaying) viewModel.startPlaybackFromPosition(bar) else viewModel.setPlayheadPosition(bar)
                                })
                                project.tracks.forEachIndexed { index, track ->
                                    TrackLane(
                                        regions = track.regions, color = track.color, isEven = index % 2 == 0,
                                        totalBars = project.totalBars, currentBar = project.playheadPosition,
                                        selectedRegionId = selectedRegionId,
                                        touchedRegionId = touchedRegionId,
                                        onRegionTap = { viewModel.selectRegion(it) },
                                        onRegionDrag = { id, newStart -> viewModel.moveRegion(id, newStart) },
                                        onRegionDragStart = { touchedRegionId = it },
                                        onRegionDragEnd = { touchedRegionId = null },
                                        onBackgroundTap = { bar ->
                                            if (isPlaying) viewModel.startPlaybackFromPosition(bar) else viewModel.setPlayheadPosition(bar)
                                        },
                                        barWidthDp = barWidth,
                                    )
                                }
                            }
                          }
                          // Horizontal scrollbar
                          TimelineScrollBar(
                              scrollPx = horizontalScrollState.value,
                              onScrollPxChange = { target ->
                                  coroutineScope.launch {
                                      horizontalScrollState.scrollTo(target)
                                  }
                              },
                              maxScrollPx = horizontalScrollState.maxValue,
                              totalWidthDp = barWidth * project.totalBars,
                              modifier = Modifier
                                  .fillMaxWidth()
                                  .height(16.dp)
                                  .background(Color(0xFF111111))
                                  .padding(horizontal = 4.dp, vertical = 3.dp),
                          )
                        }
                    }
                }

            // Inspector
            if (isInspectorVisible) {
                val sel = project.tracks.find { it.id == selectedTrackId }
                if (sel != null) {
                    InspectorPanel(
                        trackName = sel.name, trackType = sel.type.label, volume = sel.volume,
                        pan = sel.pan, sampleRate = sel.sampleRate, channels = sel.channels, bitDepth = sel.bitDepth,
                        eqLow = formatEqDb(sel.eqLow), eqMid = formatEqDb(sel.eqMid), eqHigh = formatEqDb(sel.eqHigh),
                        eqLowValue = sel.eqLow, eqMidValue = sel.eqMid, eqHighValue = sel.eqHigh,
                        onEqChange = { low, mid, high -> viewModel.setTrackEq(sel.id, low, mid, high) },
                        effects = sel.effects.map { it.name to it.isEnabled },
                        inputSource = sel.inputSource,
                        availableInputs = availableInputs,
                        onInputSourceChange = { viewModel.setTrackInput(sel.id, it) },
                        onDelete = { deleteTrackId = sel.id; showDeleteDialog = true },
                        onVolumeChange = { newVol -> viewModel.setTrackVolume(sel.id, newVol) },
                        onPanChange = { newPan -> viewModel.setTrackPan(sel.id, newPan) },
                        modifier = Modifier.width(280.dp),
                    )
                }
            }
            }
        }

        // Transport
        Row(modifier = Modifier.fillMaxWidth().height(140.dp).background(Color(0xFF111111))) {
            Column(modifier = Modifier.width(220.dp).fillMaxHeight().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                val bar = project.playheadPosition.toInt().coerceIn(1, project.totalBars)
                val beat = ((project.playheadPosition % 1f) * 4).toInt().coerceIn(0, 3) + 1
                val msPerBar = (60_000.0 / project.bpm) * 4
                val totalMs = ((project.playheadPosition - 1f) * msPerBar).toLong()
                Text(String.format("%d:%02d:00", bar, beat), color = AccentRed, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, letterSpacing = 1.sp, maxLines = 1)
                Text("Bars : Beats : Ticks", color = TextMuted, fontSize = 10.sp)
                Text(String.format("%02d:%02d", (totalMs / 60000).toInt(), ((totalMs % 60000) / 1000).toInt()), color = AccentGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
            Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                TransportBar(
                    bpm = project.bpm, timeSignature = "${project.timeSignatureNumerator}/${project.timeSignatureDenominator}",
                    isPlaying = isPlaying && !isPaused, isRecording = isRecording, isLooping = project.isLooping, isClickOn = project.isClickOn,
                    onPlay = { viewModel.togglePlayback() }, onStop = { viewModel.stopPlayback() },
                    onRecord = { (context as? MainActivity)?.requestRecordPermission { viewModel.toggleRecord() } },
                    onGoToStart = { viewModel.goToStart() }, onGoToEnd = { viewModel.goToEnd() },
                    onRewind = { viewModel.rewind() }, onFastForward = { viewModel.fastForward() },
                    onLoopToggle = { viewModel.toggleLoop() }, onClickToggle = { viewModel.toggleClick() },
                    onBpmClick = { showBpmDialog = true },
                    onTimeSigClick = { showTimeSigDialog = true },
                )
            }
            if (activeTab != "Mix") {
                MiniMixerBar(tracks = project.tracks, onExport = { exportFileName = "${project.name}_export"; showExportDialog = true },
                    onImport = { filePickerLauncher.launch(arrayOf("audio/*")) },
                    onTrackVolumeChange = { trackId, vol -> viewModel.setTrackVolume(trackId, vol) },
                    masterVolume = masterVolume,
                    onMasterVolumeChange = { vol -> viewModel.setMasterVolume(vol) },
                    masterPan = masterPan,
                    onMasterPanChange = { pan -> viewModel.setMasterPan(pan) })
            }
        }
    }

    // Rename Dialog
    if (renamingTrackId != null) {
        AlertDialog(
            onDismissRequest = { renamingTrackId = null },
            containerColor = Color(0xFF1A1A1A), titleContentColor = TextPrimary,
            title = { Text("Rename Track") },
            text = {
                OutlinedTextField(
                    value = renameText, onValueChange = { renameText = it }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = AccentRed, unfocusedBorderColor = DarkBorderLight, cursorColor = AccentRed),
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.renameTrack(renamingTrackId!!, renameText); renamingTrackId = null }) { Text("OK", color = AccentRed) } },
            dismissButton = { TextButton(onClick = { renamingTrackId = null }) { Text("Cancel", color = TextMuted) } },
        )
    }

    // Save Dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = Color(0xFF1A1A1A), titleContentColor = TextPrimary,
            title = { Text("Save Project") },
            text = {
                Column {
                    OutlinedTextField(
                        value = saveNameText, onValueChange = { saveNameText = it }, singleLine = true,
                        label = { Text("Project Name", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = AccentRed, unfocusedBorderColor = DarkBorderLight, cursorColor = AccentRed),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Save ke: /storage/emulated/0/Music/${saveNameText.replace(Regex("[^a-zA-Z0-9 _-]"), "_")}.sbrk/", color = TextMuted, fontSize = 11.sp)
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.saveProject(saveNameText); showSaveDialog = false }) { Text("Save", color = AccentRed) } },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("Cancel", color = TextMuted) } },
        )
    }

    // Export Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            containerColor = Color(0xFF1A1A1A), titleContentColor = TextPrimary,
            title = { Text("Export WAV") },
            text = {
                Column {
                    OutlinedTextField(
                        value = exportFileName, onValueChange = { exportFileName = it.filter { c -> c.isLetterOrDigit() || c == ' ' || c == '_' || c == '-' } },
                        singleLine = true,
                        label = { Text("File Name", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = AccentGreen, unfocusedBorderColor = DarkBorderLight, cursorColor = AccentGreen),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Folder:", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(exportFolderPath, color = TextPrimary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { folderPickerLauncher.launch(null) }) {
                            Text("Ganti", color = AccentGreen, fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Format: WAV 44.1kHz, 16-bit stereo", color = TextMuted, fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val safeName = exportFileName.replace(Regex("[^a-zA-Z0-9 _-]"), "_")
                    viewModel.exportWav(safeName, exportFolderPath, exportFolderUri)
                    showExportDialog = false
                }) { Text("Export", color = AccentGreen) }
            },
            dismissButton = { TextButton(onClick = { showExportDialog = false }) { Text("Cancel", color = TextMuted) } },
        )
    }

    // Delete Track Dialog
    if (showDeleteDialog && deleteTrackId != null) {
        val trackName = project.tracks.find { it.id == deleteTrackId }?.name ?: ""
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; deleteTrackId = null },
            containerColor = Color(0xFF1A1A1A), titleContentColor = TextPrimary,
            title = { Text("Hapus Track") },
            text = { Text("Hapus track \"$trackName\" dan semua audionya?", color = TextSecondary) },
            confirmButton = { TextButton(onClick = { viewModel.removeTrack(deleteTrackId!!); showDeleteDialog = false; deleteTrackId = null }) { Text("Hapus", color = AccentRed) } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false; deleteTrackId = null }) { Text("Batal", color = TextMuted) } },
        )
    }

    // BPM Dialog
    if (showBpmDialog) {
        AlertDialog(
            onDismissRequest = { showBpmDialog = false },
            containerColor = Color(0xFF1A1A1A), titleContentColor = TextPrimary,
            title = { Text("Set BPM") },
            text = {
                OutlinedTextField(
                    value = bpmText, onValueChange = { bpmText = it.filter { c -> c.isDigit() } },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = AccentOrange, unfocusedBorderColor = DarkBorderLight, cursorColor = AccentOrange),
                )
            },
            confirmButton = { TextButton(onClick = { val b = bpmText.toIntOrNull() ?: 120; viewModel.setBpm(b); showBpmDialog = false }) { Text("OK", color = AccentOrange) } },
            dismissButton = { TextButton(onClick = { showBpmDialog = false }) { Text("Cancel", color = TextMuted) } },
        )
    }

    // Time Signature Dialog
    if (showTimeSigDialog) {
        val presets = listOf(4 to 4, 3 to 4, 2 to 4, 6 to 8, 2 to 2, 5 to 4, 7 to 8, 12 to 8)
        var customNum by remember { mutableStateOf("4") }
        var customDen by remember { mutableStateOf("4") }
        var isCustom by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showTimeSigDialog = false },
            containerColor = Color(0xFF1A1A1A), titleContentColor = TextPrimary,
            title = { Text("Time Signature") },
            text = {
                Column {
                    // Preset grid: 4 columns
                    for (row in presets.chunked(4)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for ((num, den) in row) {
                                val isSelected = !isCustom && project.timeSignatureNumerator == num && project.timeSignatureDenominator == den
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) AccentBlue.copy(alpha = 0.3f) else Color(0xFF252525))
                                        .clickable {
                                            isCustom = false
                                            viewModel.setTimeSignature(num, den)
                                            showTimeSigDialog = false
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("$num/$den", color = if (isSelected) AccentBlue else TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    // Custom row
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = customNum, onValueChange = { customNum = it.filter { c -> c.isDigit() } },
                            singleLine = true, modifier = Modifier.width(50.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = AccentBlue, unfocusedBorderColor = DarkBorderLight, cursorColor = AccentBlue),
                        )
                        Text("/", color = TextMuted, fontSize = 16.sp)
                        OutlinedTextField(
                            value = customDen, onValueChange = { customDen = it.filter { c -> c.isDigit() } },
                            singleLine = true, modifier = Modifier.width(50.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = AccentBlue, unfocusedBorderColor = DarkBorderLight, cursorColor = AccentBlue),
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            val n = customNum.toIntOrNull() ?: 4
                            val d = customDen.toIntOrNull() ?: 4
                            viewModel.setTimeSignature(n, d)
                            showTimeSigDialog = false
                        }) {
                            Text("Apply", color = AccentBlue)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showTimeSigDialog = false }) { Text("Close", color = TextMuted) } },
        )
    }
}

@Composable
private fun TransportBar(
    bpm: Int, timeSignature: String, isPlaying: Boolean, isRecording: Boolean,
    isLooping: Boolean, isClickOn: Boolean,
    onPlay: () -> Unit, onStop: () -> Unit, onRecord: () -> Unit,
    onGoToStart: () -> Unit, onGoToEnd: () -> Unit,
    onRewind: () -> Unit, onFastForward: () -> Unit,
    onLoopToggle: () -> Unit, onClickToggle: () -> Unit,
    onBpmClick: () -> Unit = {},
    onTimeSigClick: () -> Unit = {},
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TransportButton("⏮", false, TextSecondary) { onGoToStart() }
            TransportButton("◀◀", false, TextSecondary) { onRewind() }
            TransportButton(if (isPlaying) "❚❚" else "▶", isPlaying, AccentGreen, 52.dp) { onPlay() }
            TransportButton("●", isRecording, TransportRed, 52.dp) { onRecord() }
            TransportButton("■", false, TextSecondary, 52.dp) { onStop() }
            TransportButton("▶▶", false, TextSecondary) { onFastForward() }
            TransportButton("⏭", false, TextSecondary) { onGoToEnd() }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.clickable { onBpmClick() }) { Text("BPM", color = TextMuted, fontSize = 12.sp); Text("$bpm", color = AccentOrange, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.clickable { onTimeSigClick() }) { Text("Time Sig", color = TextMuted, fontSize = 12.sp); Text(timeSignature, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { MiniToggle(isLooping, AccentGreen, onLoopToggle); Text("Loop", color = TextMuted, fontSize = 12.sp) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Text("Click", color = TextMuted, fontSize = 12.sp); MiniToggle(isClickOn, AccentOrange, onClickToggle) }
        }
    }
}

@Composable
private fun TransportButton(symbol: String, isActive: Boolean, activeColor: Color, size: androidx.compose.ui.unit.Dp = 44.dp, onClick: () -> Unit) {
    Box(modifier = Modifier.size(size).background(if (isActive) activeColor else Color(0xFF1A1A1A), shape = androidx.compose.foundation.shape.CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center) {
        Text(symbol, color = when { isActive && activeColor == TransportRed -> Color.White; isActive && activeColor == AccentGreen -> Color.Black; isActive -> Color.White; else -> TextSecondary }, fontSize = 18.sp)
    }
}

@Composable
private fun MiniToggle(isOn: Boolean, activeColor: Color, onToggle: () -> Unit) {
    Box(modifier = Modifier.size(width = 32.dp, height = 18.dp).background(if (isOn) activeColor else DarkBorderLight, RoundedCornerShape(9.dp)).clickable { onToggle() }) {
        Box(modifier = Modifier.offset(x = if (isOn) 16.dp else 2.dp, y = 2.dp).size(14.dp).background(Color.White, shape = androidx.compose.foundation.shape.CircleShape))
    }
}

@Composable
private fun PanelHeader(title: String, onAdd: (() -> Unit)? = null) {
    Row(modifier = Modifier.fillMaxWidth().height(TOOLBAR_HEIGHT).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        Box(modifier = Modifier.size(22.dp).background(AccentRed, RoundedCornerShape(4.dp)).let { if (onAdd != null) it.clickable { onAdd() } else it }, contentAlignment = Alignment.Center) { Text("+", color = Color.White, fontSize = 14.sp) }
    }
}

@Composable
private fun MiniMixerBar(tracks: List<Track>, onExport: () -> Unit, onImport: () -> Unit, onTrackVolumeChange: (Int, Float) -> Unit = { _, _ -> }, masterVolume: Float = 0.78f, onMasterVolumeChange: (Float) -> Unit = {}, masterPan: Float = 0.5f, onMasterPanChange: (Float) -> Unit = {}) {
    Column(modifier = Modifier.width(280.dp).fillMaxHeight().background(Color(0xFF0E0E0E)).padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Mixer", color = TextMuted, fontSize = 10.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Import", color = AccentBlue, fontSize = 10.sp, modifier = Modifier.clickable { onImport() })
                Text("Export", color = AccentRed, fontSize = 10.sp, modifier = Modifier.clickable { onExport() })
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
            tracks.forEach { track ->
                MiniChannelFader(label = track.name.take(3).uppercase(),
                    dbValue = "${((track.volume - 0.5f) * 20).toInt().let { if (it >= 0) "+$it" else "$it" }}",
                    volume = track.volume, color = track.color,
                    onVolumeChange = { newVol -> onTrackVolumeChange(track.id, newVol) })
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MiniChannelFader(label = "MST", dbValue = "${((masterVolume - 0.5f) * 20).toInt().let { if (it >= 0) "+$it" else "$it" }}", volume = masterVolume, color = AccentRed, onVolumeChange = onMasterVolumeChange)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MiniMixerPan(pan = masterPan, onPanChange = onMasterPanChange)
            }
        }
    }
}

@Composable
private fun MiniMixerPan(pan: Float, onPanChange: (Float) -> Unit) {
    val currentOnPanChange = rememberUpdatedState(onPanChange)
    val faderHeight = 80.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("PAN", color = TextMuted, fontSize = 8.sp)
        Spacer(modifier = Modifier.height(4.dp))
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
                                    currentOnPanChange.value(fraction)
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // Center tick mark (orange)
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(AccentOrange)
            )
            // Thumb
            val density = LocalDensity.current
            val thumbOffsetPx = (0.5f - pan) * with(density) { faderHeight.toPx() }
            val thumbOffset = with(density) { thumbOffsetPx.toDp() }
            Box(
                modifier = Modifier
                    .offset(y = thumbOffset)
                    .width(18.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFEEEEEE))
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(when {
            pan < 0.45f -> "L${((0.5f - pan) * 200).toInt()}"
            pan > 0.55f -> "R${((pan - 0.5f) * 200).toInt()}"
            else -> "C"
        }, color = TextMuted, fontSize = 8.sp)
    }
}
