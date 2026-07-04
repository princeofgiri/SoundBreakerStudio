package id.soundbreaker.studio.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import android.os.Environment
import android.util.Log
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.soundbreaker.studio.audio.AudioEngine
import id.soundbreaker.studio.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val audioEngine = AudioEngine()
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _project = MutableStateFlow(ProjectState())
    val project: StateFlow<ProjectState> = _project.asStateFlow()

    private val _selectedTrackId = MutableStateFlow(1)
    val selectedTrackId: StateFlow<Int> = _selectedTrackId.asStateFlow()

    private val _activeTab = MutableStateFlow("Edit")
    val activeTab: StateFlow<String> = _activeTab.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _isInspectorVisible = MutableStateFlow(true)
    val isInspectorVisible: StateFlow<Boolean> = _isInspectorVisible.asStateFlow()

    private val _selectedRegionId = MutableStateFlow<Int?>(null)
    val selectedRegionId: StateFlow<Int?> = _selectedRegionId.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _trackPcmData = mutableMapOf<Int, ShortArray>()
    private var playheadJob: Job? = null
    private var regionIdCounter = 100
    private var nextTrackId = 1
    private val totalBars = 200

    init {
        audioEngine.onAmplitude = { _amplitude.value = it }
        audioEngine.onPlaybackPosition = { current, _ ->
            val timeMs = (current.toLong() * 1000) / AudioEngine.SAMPLE_RATE
            val msPerBar = (60_000L / _project.value.bpm) * 4
            val pos = (timeMs.toFloat() / msPerBar) + 1f
            _project.value = _project.value.copy(playheadPosition = pos.coerceAtMost(totalBars.toFloat()))
        }
        audioEngine.onPlaybackFinished = {
            _isPlaying.value = false
            _isPaused.value = false
            _project.value = _project.value.copy(isPlaying = false, playheadPosition = 1f)
        }
    }

    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            getApplication(), android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startRecording() {
        if (!hasRecordPermission()) { _message.value = "Izin rekam diperlukan"; return }
        if (!_project.value.tracks.any { it.isArmed }) { _message.value = "Arm track dulu (tap R)"; return }

        _isRecording.value = true
        _project.value = _project.value.copy(isRecording = true, playheadPosition = 1f)
        startPlayheadTimer()
        audioEngine.startRecording(getRecordFile())
    }

    fun stopRecording() {
        audioEngine.stopRecording()
        _isRecording.value = false
        _project.value = _project.value.copy(isRecording = false)
        stopPlayheadTimer()
        finalizeRecording()
    }

    private fun finalizeRecording() {
        val pcm = audioEngine.getRecordedPcm()
        if (pcm.isEmpty()) return

        val armedTrackId = _project.value.tracks.find { it.isArmed }?.id ?: _selectedTrackId.value
        _trackPcmData[armedTrackId] = pcm

        val bpm = _project.value.bpm
        val msPerBar = (60_000L / bpm) * 4
        val durationMs = (pcm.size.toLong() * 1000) / (AudioEngine.SAMPLE_RATE * AudioEngine.CHANNELS)
        val widthBars = (durationMs.toFloat() / msPerBar).coerceAtLeast(1f).coerceAtMost((totalBars - 1).toFloat())
        val waveform = audioEngine.generateWaveformFromRegion(pcm, AudioEngine.CHANNELS, 1f, widthBars, totalBars)

        regionIdCounter++
        updateTrack(armedTrackId) { track ->
            val cleaned = track.regions.filter { it.name != "Recording..." }
            track.copy(regions = cleaned + AudioRegion(regionIdCounter, "Recording.wav", 1f, widthBars, waveform))
        }
        _project.value = _project.value.copy(playheadPosition = 1f)
        _message.value = "Rekaman selesai (${audioEngine.getDurationMs()}ms)"
    }

    fun togglePlayback() {
        if (_isPlaying.value) {
            if (_isPaused.value) resumePlayback() else pausePlayback()
        } else startPlayback()
    }

    private fun getFilteredPcmList(): List<ShortArray> {
        val tracks = _project.value.tracks
        val hasSolo = tracks.any { it.isSolo }
        return tracks.map { track ->
            val pcm = _trackPcmData[track.id] ?: ShortArray(0)
            if (pcm.isEmpty()) return@map pcm
            val shouldPlay = if (hasSolo) track.isSolo else !track.isMuted
            if (shouldPlay) pcm else ShortArray(0)
        }
    }

    private fun getTrackVolumes(): List<Float> {
        val tracks = _project.value.tracks
        val hasSolo = tracks.any { it.isSolo }
        return tracks.map { track ->
            val shouldPlay = if (hasSolo) track.isSolo else !track.isMuted
            if (shouldPlay) track.volume else 0f
        }
    }

    private fun getTrackEq(): List<Triple<Float, Float, Float>> {
        return _project.value.tracks.map { Triple(it.eqLow, it.eqMid, it.eqHigh) }
    }

    private fun getTrackPans(): List<Float> {
        return _project.value.tracks.map { it.pan }
    }

    fun startPlayback() {
        val pcmList = getFilteredPcmList()
        if (pcmList.all { it.isEmpty() }) { _message.value = "Tidak ada audio"; return }
        _isPlaying.value = true
        _isRecording.value = false
        val msPerBar = (60_000.0 / _project.value.bpm) * 4
        val posMs = ((_project.value.playheadPosition - 1f) * msPerBar).toLong()
        val startFrame = (posMs * AudioEngine.SAMPLE_RATE / 1000).toInt().coerceAtLeast(0)
        _project.value = _project.value.copy(isPlaying = true)
        audioEngine.startPlaybackFromPosition(pcmList, startFrame, getTrackVolumes(), getTrackPans(), getTrackEq())
    }

    fun startPlaybackFromPosition(bar: Float) {
        val pcmList = getFilteredPcmList()
        if (pcmList.all { it.isEmpty() }) { _message.value = "Tidak ada audio"; return }
        val msPerBar = (60_000.0 / _project.value.bpm) * 4
        val posMs = ((bar - 1f) * msPerBar).toLong()
        val startFrame = (posMs * AudioEngine.SAMPLE_RATE / 1000).toInt().coerceAtLeast(0)
        _isPlaying.value = true
        _isRecording.value = false
        _project.value = _project.value.copy(isPlaying = true, playheadPosition = bar)
        audioEngine.startPlaybackFromPosition(pcmList, startFrame, getTrackVolumes(), getTrackPans(), getTrackEq())
    }

    fun stopPlayback() {
        audioEngine.stopPlayback()
        _isPlaying.value = false
        _isPaused.value = false
        stopPlayheadTimer()
        _project.value = _project.value.copy(isPlaying = false, playheadPosition = 1f)
    }

    private fun pausePlayback() {
        audioEngine.pausePlayback()
        _isPaused.value = true
        stopPlayheadTimer()
    }

    private fun resumePlayback() {
        audioEngine.resumePlayback()
        _isPaused.value = false
    }

    fun toggleRecord() { if (_isRecording.value) stopRecording() else startRecording() }
    fun toggleArm(trackId: Int) { updateTrack(trackId) { it.copy(isArmed = !it.isArmed) } }
    fun toggleMute(trackId: Int) {
        updateTrack(trackId) { it.copy(isMuted = !it.isMuted) }
        if (_isPlaying.value) audioEngine.updatePlaybackBuffers(getFilteredPcmList(), getTrackVolumes(), getTrackPans(), getTrackEq())
    }
    fun toggleSolo(trackId: Int) {
        updateTrack(trackId) { it.copy(isSolo = !it.isSolo) }
        if (_isPlaying.value) audioEngine.updatePlaybackBuffers(getFilteredPcmList(), getTrackVolumes(), getTrackPans(), getTrackEq())
    }
    fun setTrackVolume(trackId: Int, volume: Float) {
        updateTrack(trackId) { it.copy(volume = volume.coerceIn(0f, 1f)) }
        if (_isPlaying.value) audioEngine.updatePlaybackBuffers(getFilteredPcmList(), getTrackVolumes(), getTrackPans(), getTrackEq())
    }
    fun setTrackPan(trackId: Int, pan: Float) {
        updateTrack(trackId) { it.copy(pan = pan.coerceIn(0f, 1f)) }
        if (_isPlaying.value) audioEngine.updatePlaybackBuffers(getFilteredPcmList(), getTrackVolumes(), getTrackPans(), getTrackEq())
    }
    fun setTrackEq(trackId: Int, low: Float, mid: Float, high: Float) {
        updateTrack(trackId) { it.copy(eqLow = low, eqMid = mid, eqHigh = high) }
        if (_isPlaying.value) audioEngine.updatePlaybackBuffers(getFilteredPcmList(), getTrackVolumes(), getTrackPans(), getTrackEq())
    }
    fun toggleLoop() { _project.value = _project.value.copy(isLooping = !_project.value.isLooping) }
    fun toggleClick() { _project.value = _project.value.copy(isClickOn = !_project.value.isClickOn) }
    fun toggleInspector() {
        _isInspectorVisible.value = !_isInspectorVisible.value
    }

    fun newProject() {
        audioEngine.stopPlayback()
        audioEngine.stopRecording()
        stopPlayheadTimer()
        _trackPcmData.clear()
        _project.value = ProjectState()
        _selectedTrackId.value = -1
        _selectedRegionId.value = null
        _isPlaying.value = false
        _isRecording.value = false
        _isPaused.value = false
        regionIdCounter = 0
        nextTrackId = 1
        _message.value = "New project"
    }
    fun selectTrack(id: Int) { _selectedTrackId.value = id }
    fun setActiveTab(tab: String) { _activeTab.value = tab }

    fun goToStart() {
        stopPlayback()
        _project.value = _project.value.copy(playheadPosition = 1f)
    }

    fun goToEnd() {
        val endPos = getLastBarPosition()
        _project.value = _project.value.copy(playheadPosition = endPos)
    }

    fun rewind() {
        val newPos = (_project.value.playheadPosition - 4f).coerceAtLeast(1f)
        _project.value = _project.value.copy(playheadPosition = newPos)
    }

    fun fastForward() {
        val newPos = (_project.value.playheadPosition + 4f).coerceAtMost(totalBars.toFloat())
        _project.value = _project.value.copy(playheadPosition = newPos)
    }

    fun nudgeRegionLeft() { _selectedRegionId.value?.let { nudgeRegion(it, -0.25f) } }
    fun nudgeRegionRight() { _selectedRegionId.value?.let { nudgeRegion(it, 0.25f) } }

    private fun nudgeRegion(regionId: Int, delta: Float) {
        val trackId = _project.value.tracks.find { it.regions.any { r -> r.id == regionId } }?.id ?: return
        updateTrack(trackId) { track ->
            track.copy(regions = track.regions.map { r ->
                if (r.id == regionId) r.copy(startBar = (r.startBar + delta).coerceAtLeast(0.5f)) else r
            })
        }
    }

    fun moveRegion(regionId: Int, newStartBar: Float) {
        val trackId = _project.value.tracks.find { it.regions.any { r -> r.id == regionId } }?.id ?: return
        updateTrack(trackId) { track ->
            track.copy(regions = track.regions.map { r ->
                if (r.id == regionId) r.copy(startBar = newStartBar.coerceAtLeast(0.5f)) else r
            })
        }
    }

    fun cutRegion() {
        val regionId = _selectedRegionId.value ?: run { _message.value = "Pilih region dulu"; return }
        val pos = _project.value.playheadPosition
        val trackId = _project.value.tracks.find { it.regions.any { r -> r.id == regionId } }?.id ?: return
        val track = _project.value.tracks.find { it.id == trackId } ?: return
        val region = track.regions.find { it.id == regionId } ?: return

        if (pos <= region.startBar || pos >= region.startBar + region.widthBars) {
            _message.value = "Playhead harus di dalam region"; return
        }

        regionIdCounter++
        val left = AudioRegion(region.id, region.name, region.startBar, pos - region.startBar)
        val right = AudioRegion(regionIdCounter, region.name, pos, region.startBar + region.widthBars - pos)

        updateTrack(trackId) { t -> t.copy(regions = t.regions.map { if (it.id == regionId) left else it } + right) }
        _selectedRegionId.value = null
        _message.value = "Region dipotong"
    }

    fun selectRegion(id: Int) { _selectedRegionId.value = id }

    fun deleteSelectedRegion() {
        val regionId = _selectedRegionId.value ?: return
        val trackId = _project.value.tracks.find { it.regions.any { r -> r.id == regionId } }?.id ?: return
        updateTrack(trackId) { track -> track.copy(regions = track.regions.filter { it.id != regionId }) }
        _selectedRegionId.value = null
        _message.value = "Region dihapus"
    }

    fun setPlayheadPosition(position: Float) {
        val pos = position.coerceIn(1f, totalBars.toFloat())
        _project.value = _project.value.copy(playheadPosition = pos)
    }

    fun renameTrack(trackId: Int, newName: String) {
        if (newName.isBlank()) return
        updateTrack(trackId) { it.copy(name = newName.trim()) }
    }

    fun addTrack(type: TrackType = TrackType.AUDIO_STEREO) {
        val colors = listOf(0xFFFF4757L, 0xFF3498DBL, 0xFF2ED573L, 0xFFFFA502L, 0xFFA29BFEL, 0xFFFD79A8L, 0xFF00CEC9L, 0xFFE17055L, 0xFF6C5CE7L)
        val names = mapOf(TrackType.AUDIO_MONO to "Audio", TrackType.AUDIO_STEREO to "Audio", TrackType.MIDI_INSTRUMENT to "MIDI", TrackType.MIDI_DRUM to "Drums")
        val count = _project.value.tracks.size
        val track = Track(nextTrackId++, "${names[type] ?: "Track"} ${count + 1}", type, Color(colors[count % colors.size]))
        _project.value = _project.value.copy(tracks = _project.value.tracks + track)
        _selectedTrackId.value = track.id
    }

    fun removeTrack(trackId: Int) {
        _project.value = _project.value.copy(tracks = _project.value.tracks.filter { it.id != trackId })
        if (_selectedTrackId.value == trackId) _selectedTrackId.value = _project.value.tracks.firstOrNull()?.id ?: -1
    }

    fun importAudio(uri: android.net.Uri) {
        val context = getApplication<Application>()
        _message.value = "Mengimport audio..."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = audioEngine.readAudioFile(uri, context) ?: run {
                    withContext(Dispatchers.Main) { _message.value = "Gagal membaca file audio" }
                    return@launch
                }
                val (pcmData, channels, statusMsg) = result
                if (pcmData.isEmpty()) { withContext(Dispatchers.Main) { _message.value = "File kosong" }; return@launch }

                withContext(Dispatchers.Main) {
                    if (_project.value.tracks.isEmpty() || (_trackPcmData[_selectedTrackId.value]?.isNotEmpty() == true)) addTrack()

                    val targetId = _selectedTrackId.value
                    _trackPcmData[targetId] = pcmData

                    val msPerBar = (60_000L / _project.value.bpm) * 4
                    val durationMs = (pcmData.size.toLong() * 1000) / (AudioEngine.SAMPLE_RATE * channels)
                    val widthBars = (durationMs.toFloat() / msPerBar).coerceAtLeast(1f).coerceAtMost((totalBars - 1).toFloat())
                    val waveform = audioEngine.generateWaveformFromRegion(pcmData, channels, 1f, widthBars, totalBars)

                    val fileName = getFileName(uri, context)
                    regionIdCounter++
                    updateTrack(targetId) { track ->
                        track.copy(
                            channels = channels,
                            bitDepth = AudioEngine.BITS_PER_SAMPLE,
                            regions = track.regions + AudioRegion(regionIdCounter, fileName, 1f, widthBars, waveform)
                        )
                    }
                    _message.value = "Imported: $fileName ($statusMsg)"
                }
            } catch (e: Exception) {
                Log.e("SoundBreaker", "Import error", e)
                withContext(Dispatchers.Main) { _message.value = "Gagal import: ${e.message}" }
            }
        }
    }

    fun exportWav(uri: android.net.Uri) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pcm = _trackPcmData.values.firstOrNull()
                if (pcm == null) { withContext(Dispatchers.Main) { _message.value = "Tidak ada audio" }; return@launch }
                val os = context.contentResolver.openOutputStream(uri) ?: return@launch
                val baos = java.io.ByteArrayOutputStream()
                audioEngine.writeWavToStream(pcm, baos)
                os.write(baos.toByteArray())
                os.close()
                withContext(Dispatchers.Main) { _message.value = "Export WAV berhasil" }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _message.value = "Gagal export: ${e.message}" }
            }
        }
    }

    fun saveProject(customName: String? = null) {
        val context = getApplication<Application>()
        android.util.Log.e("SoundBreaker", "saveProject called, name=$customName")

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                android.util.Log.e("SoundBreaker", "Storage permission not granted")
                val act = context as? id.soundbreaker.studio.MainActivity
                act?.requestStoragePermission { saveProject(customName) }
                return
            }
        }

        val projectName = customName?.takeIf { it.isNotBlank() } ?: _project.value.name

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val projectDir = File("/storage/emulated/0/Music").apply { mkdirs() }
                val safeName = projectName.replace(Regex("[^a-zA-Z0-9 _-]"), "_")
                val dir = File(projectDir, "$safeName.sbrk").apply { mkdirs() }
                android.util.Log.e("SoundBreaker", "Saving to: ${dir.absolutePath}")

                val trackDataList = mutableListOf<JSONObject>()
                for ((trackId, pcmData) in _trackPcmData) {
                    val track = _project.value.tracks.find { it.id == trackId } ?: continue
                    val wavName = track.name.replace(Regex("[^a-zA-Z0-9 _-]"), "_") + ".wav"
                    val wavFile = File(dir, wavName)
                    audioEngine.writeWavFile(pcmData, wavFile)

                    val regions = JSONArray()
                    for (r in track.regions) {
                        regions.put(JSONObject().apply {
                            put("id", r.id); put("name", r.name)
                            put("startBar", r.startBar.toDouble())
                            put("widthBars", r.widthBars.toDouble())
                        })
                    }
                    trackDataList.add(JSONObject().apply {
                        put("id", track.id); put("name", track.name)
                        put("type", track.type.name)
                        put("color", String.format("#%02X%02X%02X", (track.color.red * 255).toInt(), (track.color.green * 255).toInt(), (track.color.blue * 255).toInt()))
                        put("volume", track.volume.toDouble()); put("pan", track.pan.toDouble())
                        put("audioFile", wavName); put("regions", regions)
                    })
                }

                val root = JSONObject().apply {
                    put("name", projectName)
                    put("bpm", _project.value.bpm)
                    put("isLooping", _project.value.isLooping)
                    put("isClickOn", _project.value.isClickOn)
                    put("tracks", JSONArray(trackDataList))
                }

                File(dir, "project.json").writeText(root.toString(2))
                _project.value = _project.value.copy(name = projectName)
                withContext(Dispatchers.Main) { _message.value = "Project saved: ${dir.name}" }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _message.value = "Gagal save: ${e.message}" }
            }
        }
    }

    fun openProject(uri: android.net.Uri) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.readBytes()?.toString(Charsets.UTF_8) ?: return@launch
                val root = JSONObject(json)

                val name = root.optString("name", "Untitled")
                val bpm = root.optInt("bpm", 120)
                val isLooping = root.optBoolean("isLooping", true)
                val isClickOn = root.optBoolean("isClickOn", false)
                val tracksJson = root.getJSONArray("tracks")

                val newTracks = mutableListOf<Track>()
                val newPcm = mutableMapOf<Int, ShortArray>()

                // Resolve project directory from content URI
                val uriPath = uri.path ?: ""
                val decodedPath = java.net.URLDecoder.decode(uriPath, "UTF-8")
                // content://com.android.externalstorage.documents/document/primary%3AMusic%2FProject.sbrk%2Fproject.json
                // -> /storage/emulated/0/Music/Project.sbrk/
                val storagePath = decodedPath.replace("/document/primary:", "/storage/emulated/0/")
                val sdcardPath = decodedPath.replace("/document/primary:", "/sdcard/")
                val projectDir = File(storagePath).parentFile ?: File(sdcardPath).parentFile

                // Try both paths
                val actualDir = if (projectDir.exists()) projectDir else File(sdcardPath).parentFile ?: projectDir
                android.util.Log.e("SoundBreaker", "Open project dir: ${actualDir.absolutePath}, exists=${actualDir.exists()}")

                for (i in 0 until tracksJson.length()) {
                    val t = tracksJson.getJSONObject(i)
                    val trackId = t.getInt("id")
                    val regions = mutableListOf<AudioRegion>()
                    val regionsJson = t.getJSONArray("regions")
                    for (j in 0 until regionsJson.length()) {
                        val r = regionsJson.getJSONObject(j)
                        regions.add(AudioRegion(r.getInt("id"), r.getString("name"),
                            r.getDouble("startBar").toFloat(), r.getDouble("widthBars").toFloat()))
                    }

                    val type = try { TrackType.valueOf(t.getString("type")) } catch (e: Exception) { TrackType.AUDIO_STEREO }
                    val colorHex = t.optString("color", "#FF4757")
                    android.util.Log.e("SoundBreaker", "Track color: id=$trackId, hex=$colorHex")
                    val color = try { Color(android.graphics.Color.parseColor(colorHex)) } catch (e: Exception) {
                        android.util.Log.e("SoundBreaker", "Color parse failed: $colorHex", e)
                        Color(0xFFFF4757)
                    }
                    newTracks.add(Track(trackId, t.getString("name"), type, color,
                        t.getDouble("volume").toFloat(), t.getDouble("pan").toFloat(),
                        regions = regions))

                    val audioFileName = t.optString("audioFile", "")
                    if (audioFileName.isNotEmpty()) {
                        var pcm: ShortArray? = null

                        // Method 1: Direct file path
                        val wavFile = File(actualDir, audioFileName)
                        if (wavFile.exists()) {
                            android.util.Log.e("SB", "File exists: ${wavFile.absolutePath}, size=${wavFile.length()}")
                            pcm = try { audioEngine.readWavFile(wavFile) } catch (e: Exception) {
                                android.util.Log.e("SB", "readWavFile error: ${e.message}")
                                null
                            }
                            android.util.Log.e("SB", "readWavFile result: ${pcm?.size ?: "null"}")
                        } else {
                            android.util.Log.e("SB", "File NOT found: ${wavFile.absolutePath}")
                        }

                        // Method 2: Try /sdcard path
                        if (pcm == null || pcm.isEmpty()) {
                            val sdcardFile = File("/sdcard/Music/${actualDir.name}/$audioFileName")
                            android.util.Log.e("SB", "Try /sdcard: ${sdcardFile.absolutePath}, exists=${sdcardFile.exists()}")
                            if (sdcardFile.exists()) {
                                pcm = try { audioEngine.readWavFile(sdcardFile) } catch (e: Exception) { null }
                            }
                        }

                        if (pcm != null && pcm.isNotEmpty()) {
                            newPcm[trackId] = pcm
                            android.util.Log.e("SB", "Loaded OK: ${pcm.size} samples for track $trackId")
                        } else {
                            android.util.Log.e("SB", "FAILED to load audio for track $trackId")
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    if (newPcm.isEmpty()) {
                        _message.value = "Tidak ada audio yang bisa dimuat"
                    } else {
                        // Generate waveform for each loaded track
                        val tracksWithWaveform = newTracks.map { track ->
                            val pcm = newPcm[track.id]
                            if (pcm != null) {
                                val totalWidthBars = track.regions.maxOfOrNull { it.startBar + it.widthBars } ?: 20f
                                val updatedRegions = track.regions.map { region ->
                                    val waveform = audioEngine.generateWaveformFromRegion(pcm, 2, region.startBar, region.widthBars, totalWidthBars.toInt())
                                    region.copy(waveform = waveform)
                                }
                                track.copy(regions = updatedRegions)
                            } else track
                        }

                        _project.value = _project.value.copy(name = name, bpm = bpm, isLooping = isLooping, isClickOn = isClickOn, tracks = tracksWithWaveform)
                        _trackPcmData.clear()
                        _trackPcmData.putAll(newPcm)
                        regionIdCounter = tracksWithWaveform.maxOfOrNull { t -> t.regions.maxOfOrNull { it.id } ?: 0 } ?: 0
                        _message.value = "Opened: $name (${newPcm.size} tracks with audio)"
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SoundBreaker", "Open error: ${e.message}", e)
                withContext(Dispatchers.Main) { _message.value = "Gagal open: ${e.message}" }
            }
        }
    }

    fun clearMessage() { _message.value = null }

    private fun getLastBarPosition(): Float {
        var last = 1f
        for (track in _project.value.tracks) for (r in track.regions) {
            val end = r.startBar + r.widthBars
            if (end > last) last = end
        }
        return last
    }

    private fun startPlayheadTimer() {
        stopPlayheadTimer()
        val msPerBar = (60_000.0 / _project.value.bpm) * 4
        playheadJob = viewModelScope.launch {
            while (isActive) {
                delay(16)
                val elapsed = System.currentTimeMillis() - recordStartTimeMs
                val pos = (elapsed / msPerBar).toFloat() + 1f
                if (pos > totalBars) {
                    if (_project.value.isLooping) { recordStartTimeMs = System.currentTimeMillis(); _project.value = _project.value.copy(playheadPosition = 1f) }
                    else { stopRecording(); break }
                } else {
                    _project.value = _project.value.copy(playheadPosition = pos)
                }
            }
        }
    }

    private fun stopPlayheadTimer() { playheadJob?.cancel(); playheadJob = null }
    private var recordStartTimeMs = System.currentTimeMillis()

    private fun updateTrack(trackId: Int, transform: (Track) -> Track) {
        _project.value = _project.value.copy(tracks = _project.value.tracks.map {
            if (it.id == trackId) transform(it) else it
        })
    }

    private fun getRecordFile(): File {
        val dir = File(getApplication<Application>().cacheDir, "recordings").apply { mkdirs() }
        return File(dir, "rec_${System.currentTimeMillis()}.wav")
    }

    private fun getFileName(uri: android.net.Uri, context: android.content.Context): String {
        var name = "Audio"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = cursor.getString(idx)
            }
        }
        return name
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.release()
        viewModelScope.cancel()
    }
}
