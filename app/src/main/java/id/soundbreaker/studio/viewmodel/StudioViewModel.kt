package id.soundbreaker.studio.viewmodel

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.util.Log
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.soundbreaker.studio.audio.AudioEngine
import id.soundbreaker.studio.audio.AudioExporter
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

    private val _masterVolume = MutableStateFlow(0.78f)
    val masterVolume: StateFlow<Float> = _masterVolume.asStateFlow()

    private val _masterPan = MutableStateFlow(0.5f)
    val masterPan: StateFlow<Float> = _masterPan.asStateFlow()

    private val _trackAmplitudes = MutableStateFlow<List<Float>>(emptyList())
    val trackAmplitudes: StateFlow<List<Float>> = _trackAmplitudes.asStateFlow()

    private val _availableInputs = MutableStateFlow<List<String>>(listOf("Mic"))
    val availableInputs: StateFlow<List<String>> = _availableInputs.asStateFlow()

    private val _availableOutputs = MutableStateFlow<List<String>>(listOf("Speaker"))
    val availableOutputs: StateFlow<List<String>> = _availableOutputs.asStateFlow()

    private val _outputDevice = MutableStateFlow("Speaker")
    val outputDevice: StateFlow<String> = _outputDevice.asStateFlow()

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

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()
    private val _exportProgress = MutableStateFlow(0f)
    val exportProgress: StateFlow<Float> = _exportProgress.asStateFlow()

    private val _trackPcmData = mutableMapOf<Int, ShortArray>()
    private var playheadJob: Job? = null
    private var regionIdCounter = 100
    private var nextTrackId = 1
    private var recordStartBar: Float = 1f
    private val totalBars = 200

    init {
        audioEngine.onAmplitude = { _amplitude.value = it }
        audioEngine.onPlaybackPosition = playbackPos@{ current, _ ->
            if (_isRecording.value) return@playbackPos
            val timeMs = (current.toLong() * 1000) / AudioEngine.SAMPLE_RATE
            val msPerBar = getMsPerBar().toLong()
            val pos = (timeMs.toFloat() / msPerBar) + 1f
            _project.value = _project.value.copy(playheadPosition = pos.coerceAtMost(totalBars.toFloat()))
        }
        audioEngine.onPlaybackFinished = playbackDone@{
            if (_isRecording.value) return@playbackDone
            _isPlaying.value = false
            _isPaused.value = false
            _project.value = _project.value.copy(isPlaying = false, playheadPosition = 1f)
            _trackAmplitudes.value = emptyList()
        }
        audioEngine.onTrackAmplitudes = { amps ->
            _trackAmplitudes.value = amps
        }
        audioEngine.onRecordingWaveform = waveform@{ waveform ->
            val armedTrackId = _project.value.tracks.find { it.isArmed }?.id ?: return@waveform
            updateTrack(armedTrackId) { track ->
                val regions = track.regions.map { region ->
                    if (region.name == "Recording...") region.copy(waveform = waveform)
                    else region
                }
                track.copy(regions = regions)
            }
        }
        refreshAvailableInputs()
        refreshAvailableOutputs()
        val prefs = getApplication<Application>().getSharedPreferences("studio_prefs", Context.MODE_PRIVATE)
        val savedOutput = prefs.getString("output_device", null)
        val defaultOutput = savedOutput ?: _availableOutputs.value.firstOrNull() ?: "Speaker"
        setOutputDevice(defaultOutput)

        // Auto-refresh device list when audio devices change (USB plug/unplug, BT connect/disconnect)
        val audioManager = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        audioManager.registerAudioDeviceCallback(object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>) {
                refreshAvailableInputs()
                refreshAvailableOutputs()
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>) {
                refreshAvailableInputs()
                refreshAvailableOutputs()
            }
        }, null)
    }

    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            getApplication(), android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startRecording() {
        if (!hasRecordPermission()) { _message.value = "Izin rekam diperlukan"; return }
        val armedTrack = _project.value.tracks.find { it.isArmed }
        if (armedTrack == null) { _message.value = "Arm track dulu (tap R)"; return }

        recordStartBar = _project.value.playheadPosition
        val ok = audioEngine.startRecording(getRecordFile(), getApplication(), armedTrack.inputSource)
        if (!ok) { _message.value = "Gagal mulai rekam"; return }

        _isRecording.value = true
        _project.value = _project.value.copy(isRecording = true)

        regionIdCounter++
        updateTrack(armedTrack.id) { track ->
            val cleaned = track.regions.filter { it.name != "Recording..." }
            track.copy(regions = cleaned + AudioRegion(regionIdCounter, "Recording...", recordStartBar, 0.5f, null))
        }

        // Start overdub: play all existing tracks while recording
        startOverdubPlayback(recordStartBar)
        startPlayheadTimer(recordStartBar)
    }

    private fun startOverdubPlayback(fromBar: Float) {
        val pcmList = getFilteredPcmList()
        if (pcmList.all { it.isEmpty() } && !_project.value.isClickOn) return

        val msPerBar = getMsPerBar()
        val posMs = ((fromBar - 1f) * msPerBar).toLong()
        val startFrame = (posMs * AudioEngine.SAMPLE_RATE / 1000).toInt().coerceAtLeast(0)

        _project.value = _project.value.copy(isPlaying = true)
        android.util.Log.e("SB", "startOverdub: startFrame=$startFrame, clickOn=${_project.value.isClickOn}, pcmTracks=${pcmList.count { it.isNotEmpty() }}, bpm=${_project.value.bpm}, beatsPerBar=${getBeatsPerBar()}")
        audioEngine.startPlaybackFromPosition(pcmList, startFrame, getTrackVolumes(), getTrackPans(), getTrackEq(), getTrackEffects(), _project.value.bpm, _project.value.isClickOn, getTrackOffsets(), getBeatsPerBar())
    }

    fun stopRecording() {
        audioEngine.stopPlayback()
        audioEngine.stopRecording()
        _isRecording.value = false
        _isPlaying.value = false
        _project.value = _project.value.copy(isRecording = false, isPlaying = false)
        stopPlayheadTimer()
        // Delay finalizeRecording to let recordJob finish cleanup
        viewModelScope.launch {
            kotlinx.coroutines.delay(200)
            finalizeRecording()
        }
    }

    private fun finalizeRecording() {
        try {
            val pcm = audioEngine.getRecordedPcm()
            android.util.Log.e("SB", "finalizeRecording: pcm.size=${pcm.size}, recordStartBar=$recordStartBar")
            if (pcm.isEmpty()) {
                android.util.Log.e("SB", "finalizeRecording: pcm is EMPTY")
                _project.value = _project.value.copy(isRecording = false)
                return
            }

            val armedTrackId = _project.value.tracks.find { it.isArmed }?.id ?: _selectedTrackId.value
            _trackPcmData[armedTrackId] = pcm

            val msPerBar = getMsPerBar()
            val durationMs = (pcm.size.toLong() * 1000) / (AudioEngine.SAMPLE_RATE * AudioEngine.CHANNELS)
            val widthBars = (durationMs.toFloat() / msPerBar.toFloat()).coerceAtLeast(1f).coerceAtMost((totalBars - recordStartBar).toFloat())
            val waveform = audioEngine.generateWaveformFromRegion(pcm, AudioEngine.CHANNELS, 1f, widthBars, totalBars)

            regionIdCounter++
            updateTrack(armedTrackId) { track ->
                val cleaned = track.regions.filter { it.name != "Recording..." }
                track.copy(regions = cleaned + AudioRegion(regionIdCounter, "Recording.wav", recordStartBar, widthBars, waveform))
            }
            _project.value = _project.value.copy(isRecording = false, playheadPosition = recordStartBar)
            _message.value = "Rekaman selesai (${audioEngine.getDurationMs()}ms)"
            android.util.Log.e("SB", "finalizeRecording: OK, widthBars=$widthBars, startBar=$recordStartBar")
        } catch (e: Exception) {
            android.util.Log.e("SB", "finalizeRecording error: ${e.message}")
            _project.value = _project.value.copy(isRecording = false)
        }
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

    private fun getTrackEffects(): List<List<id.soundbreaker.studio.data.Effect>> {
        return _project.value.tracks.map { it.effects }
    }

    private fun getBeatsPerBar(): Int {
        val p = _project.value
        return if (p.timeSignatureDenominator == 8) p.timeSignatureNumerator / 3 else p.timeSignatureNumerator
    }

    private fun getTrackOffsets(): List<Int> {
        val bpm = _project.value.bpm
        val framesPerBar = (AudioEngine.SAMPLE_RATE.toLong() * 60 * 4) / bpm
        return _project.value.tracks.map { track ->
            val firstRegion = track.regions.firstOrNull()
            if (firstRegion != null) {
                ((firstRegion.startBar - 1f) * framesPerBar).toInt().coerceAtLeast(0)
            } else 0
        }
    }

    fun startPlayback() {
        val pcmList = getFilteredPcmList()
        if (pcmList.all { it.isEmpty() }) { _message.value = "Tidak ada audio"; return }
        _isPlaying.value = true
        _isRecording.value = false
        val msPerBar = getMsPerBar()
        val posMs = ((_project.value.playheadPosition - 1f) * msPerBar).toLong()
        val startFrame = (posMs * AudioEngine.SAMPLE_RATE / 1000).toInt().coerceAtLeast(0)
        _project.value = _project.value.copy(isPlaying = true)
        audioEngine.startPlaybackFromPosition(pcmList, startFrame, getTrackVolumes(), getTrackPans(), getTrackEq(), getTrackEffects(), _project.value.bpm, _project.value.isClickOn, getTrackOffsets(), getBeatsPerBar())
    }

    fun startPlaybackFromPosition(bar: Float) {
        val pcmList = getFilteredPcmList()
        if (pcmList.all { it.isEmpty() }) { _message.value = "Tidak ada audio"; return }
        val msPerBar = getMsPerBar()
        val posMs = ((bar - 1f) * msPerBar).toLong()
        val startFrame = (posMs * AudioEngine.SAMPLE_RATE / 1000).toInt().coerceAtLeast(0)
        _isPlaying.value = true
        _isRecording.value = false
        _project.value = _project.value.copy(isPlaying = true, playheadPosition = bar)
        audioEngine.startPlaybackFromPosition(pcmList, startFrame, getTrackVolumes(), getTrackPans(), getTrackEq(), getTrackEffects(), _project.value.bpm, _project.value.isClickOn, getTrackOffsets(), getBeatsPerBar())
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
        if (_isPlaying.value) audioEngine.updatePlaybackBuffers(getFilteredPcmList(), getTrackVolumes(), getTrackPans(), getTrackEq(), getTrackEffects(), _project.value.bpm, _project.value.isClickOn)
    }
    fun toggleSolo(trackId: Int) {
        updateTrack(trackId) { it.copy(isSolo = !it.isSolo) }
        if (_isPlaying.value) audioEngine.updatePlaybackBuffers(getFilteredPcmList(), getTrackVolumes(), getTrackPans(), getTrackEq(), getTrackEffects(), _project.value.bpm, _project.value.isClickOn)
    }
    fun setTrackVolume(trackId: Int, volume: Float) {
        updateTrack(trackId) { it.copy(volume = volume.coerceIn(0f, 1f)) }
        if (_isPlaying.value) audioEngine.updatePlaybackBuffers(getFilteredPcmList(), getTrackVolumes(), getTrackPans(), getTrackEq(), getTrackEffects(), _project.value.bpm, _project.value.isClickOn)
    }
    fun setTrackPan(trackId: Int, pan: Float) {
        updateTrack(trackId) { it.copy(pan = pan.coerceIn(0f, 1f)) }
        if (_isPlaying.value) audioEngine.updatePlaybackBuffers(getFilteredPcmList(), getTrackVolumes(), getTrackPans(), getTrackEq(), getTrackEffects(), _project.value.bpm, _project.value.isClickOn)
    }
    fun setTrackInput(trackId: Int, inputSource: String) {
        updateTrack(trackId) { it.copy(inputSource = inputSource) }
    }
    fun refreshAvailableInputs() {
        _availableInputs.value = audioEngine.getAvailableInputs(getApplication())
    }
    fun refreshAvailableOutputs() {
        _availableOutputs.value = audioEngine.getAvailableOutputs(getApplication())
    }
    fun setOutputDevice(deviceName: String) {
        _outputDevice.value = deviceName
        audioEngine.setOutputDevice(getApplication(), deviceName)
        val prefs = getApplication<Application>().getSharedPreferences("studio_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("output_device", deviceName).apply()
    }
    fun setTrackEq(trackId: Int, low: Float, mid: Float, high: Float) {
        updateTrack(trackId) { it.copy(eqLow = low, eqMid = mid, eqHigh = high) }
        if (_isPlaying.value) audioEngine.updatePlaybackBuffers(getFilteredPcmList(), getTrackVolumes(), getTrackPans(), getTrackEq(), getTrackEffects(), _project.value.bpm, _project.value.isClickOn)
    }
    fun toggleLoop() { _project.value = _project.value.copy(isLooping = !_project.value.isLooping) }
    fun toggleClick() {
        _project.value = _project.value.copy(isClickOn = !_project.value.isClickOn)
        if (_isPlaying.value) audioEngine.updatePlaybackBuffers(getFilteredPcmList(), getTrackVolumes(), getTrackPans(), getTrackEq(), getTrackEffects(), _project.value.bpm, _project.value.isClickOn)
    }
    fun setBpm(bpm: Int) {
        val oldBpm = _project.value.bpm
        val newBpm = bpm.coerceIn(20, 300)
        if (oldBpm == newBpm) return

        val ratio = newBpm.toFloat() / oldBpm
        val scaledTracks = _project.value.tracks.map { track ->
            val scaledRegions = track.regions.map { region ->
                region.copy(
                    widthBars = region.widthBars * ratio
                )
            }
            track.copy(regions = scaledRegions)
        }
        _project.value = _project.value.copy(bpm = newBpm, tracks = scaledTracks)
        if (_isPlaying.value) audioEngine.updatePlaybackBuffers(getFilteredPcmList(), getTrackVolumes(), getTrackPans(), getTrackEq(), getTrackEffects(), _project.value.bpm, _project.value.isClickOn)
    }

    fun setTimeSignature(numerator: Int, denominator: Int) {
        val n = numerator.coerceIn(1, 12)
        val d = denominator.coerceIn(2, 8)
        if (_project.value.timeSignatureNumerator == n && _project.value.timeSignatureDenominator == d) return
        _project.value = _project.value.copy(timeSignatureNumerator = n, timeSignatureDenominator = d)
    }

    fun addChord(chord: String) {
        val bar = _project.value.playheadPosition
        val chords = (_project.value.chordMarkers + ChordMarker(bar, chord)).sortedBy { it.bar }
        _project.value = _project.value.copy(chordMarkers = chords)
    }

    fun removeLastChord() {
        val chords = _project.value.chordMarkers.dropLast(1)
        _project.value = _project.value.copy(chordMarkers = chords)
    }

    fun getCurrentChord(): String? {
        val pos = _project.value.playheadPosition
        return _project.value.chordMarkers.lastOrNull { it.bar <= pos }?.chord
    }

    private fun getMsPerBar(): Double {
        val project = _project.value
        // Time signature affects beats per bar: e.g. 6/8 = 2 beats of 3 eighth notes
        val beatsPerBar = if (project.timeSignatureDenominator == 8) {
            project.timeSignatureNumerator / 3.0  // compound meter: 6/8 = 2 beats, 12/8 = 4 beats
        } else {
            project.timeSignatureNumerator.toDouble()  // simple meter: 4/4 = 4 beats, 3/4 = 3 beats
        }
        return (60_000.0 / project.bpm) * beatsPerBar
    }
    fun setMasterVolume(volume: Float) {
        _masterVolume.value = volume.coerceIn(0f, 1f)
        audioEngine.setMasterVolume(_masterVolume.value)
    }
    fun setMasterPan(pan: Float) {
        _masterPan.value = pan.coerceIn(0f, 1f)
        audioEngine.setMasterPan(_masterPan.value)
    }
    fun setMasterEqBand(index: Int, gain: Float) {
        val newBands = _project.value.masterEq.toMutableList()
        if (index in newBands.indices) newBands[index] = gain.coerceIn(-12f, 12f)
        _project.value = _project.value.copy(masterEq = newBands, masterEqPreset = "Custom")
        audioEngine.setMasterEq(newBands)
    }
    fun setMasterEqPreset(presetName: String) {
        val bands = _project.value.customPresets[presetName]
            ?: id.soundbreaker.studio.data.MasterEqPresets.presets[presetName]
            ?: List(10) { 0f }
        _project.value = _project.value.copy(masterEq = bands, masterEqPreset = presetName)
        audioEngine.setMasterEq(bands)
    }
    fun setMasterEqEnabled(enabled: Boolean) {
        _project.value = _project.value.copy(masterEqEnabled = enabled)
        audioEngine.setMasterEqEnabled(enabled)
    }
    fun saveCustomPreset(name: String, bands: List<Float>) {
        val updated = _project.value.customPresets.toMutableMap()
        updated[name] = bands.toList()
        _project.value = _project.value.copy(customPresets = updated, masterEqPreset = name)
    }
    fun deleteCustomPreset(name: String) {
        val updated = _project.value.customPresets.toMutableMap()
        updated.remove(name)
        val nextPreset = if (_project.value.masterEqPreset == name) "Flat" else _project.value.masterEqPreset
        _project.value = _project.value.copy(customPresets = updated, masterEqPreset = nextPreset)
        if (nextPreset == "Flat") {
            setMasterEqPreset("Flat")
        }
    }
    fun addEffect(trackId: Int, effectType: id.soundbreaker.studio.data.EffectType) {
        val effectId = (System.currentTimeMillis() % 100000).toInt()
        val fx = id.soundbreaker.studio.data.Effect(
            id = effectId, name = effectType.displayName,
            icon = when (effectType) {
                id.soundbreaker.studio.data.EffectType.COMPRESSOR -> "compressor"
                id.soundbreaker.studio.data.EffectType.REVERB -> "reverb"
                id.soundbreaker.studio.data.EffectType.DELAY -> "delay"
                id.soundbreaker.studio.data.EffectType.CHORUS -> "chorus"
                id.soundbreaker.studio.data.EffectType.DISTORTION -> "distortion"
                id.soundbreaker.studio.data.EffectType.FILTER -> "filter"
            },
            params = effectType.defaultParams.toMutableMap()
        )
        updateTrack(trackId) { track ->
            track.copy(effects = track.effects + fx)
        }
        if (_isPlaying.value) audioEngine.updatePlaybackBuffers(getFilteredPcmList(), getTrackVolumes(), getTrackPans(), getTrackEq(), getTrackEffects(), _project.value.bpm, _project.value.isClickOn)
    }
    fun removeEffect(trackId: Int, effectId: Int) {
        updateTrack(trackId) { track ->
            track.copy(effects = track.effects.filter { it.id != effectId })
        }
        if (_isPlaying.value) audioEngine.updatePlaybackBuffers(getFilteredPcmList(), getTrackVolumes(), getTrackPans(), getTrackEq(), getTrackEffects(), _project.value.bpm, _project.value.isClickOn)
    }
    fun toggleEffect(trackId: Int, effectId: Int) {
        updateTrack(trackId) { track ->
            track.copy(effects = track.effects.map {
                if (it.id == effectId) it.copy(isEnabled = !it.isEnabled) else it
            })
        }
        if (_isPlaying.value) audioEngine.updatePlaybackBuffers(getFilteredPcmList(), getTrackVolumes(), getTrackPans(), getTrackEq(), getTrackEffects(), _project.value.bpm, _project.value.isClickOn)
    }
    fun setEffectParam(trackId: Int, effectId: Int, paramKey: String, value: Float) {
        updateTrack(trackId) { track ->
            track.copy(effects = track.effects.map {
                if (it.id == effectId) it.copy(params = it.params + (paramKey to value)) else it
            })
        }
        if (_isPlaying.value) audioEngine.updatePlaybackBuffers(getFilteredPcmList(), getTrackVolumes(), getTrackPans(), getTrackEq(), getTrackEffects(), _project.value.bpm, _project.value.isClickOn)
    }
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
        val left = AudioRegion(region.id, region.name, region.startBar, pos - region.startBar, region.waveform, region.audioOffsetBars)
        val right = AudioRegion(regionIdCounter, region.name, pos, region.startBar + region.widthBars - pos, region.waveform, region.audioOffsetBars + (pos - region.startBar))

        updateTrack(trackId) { t -> t.copy(regions = t.regions.map { if (it.id == regionId) left else it } + right) }
        _selectedRegionId.value = null
        _message.value = "Region dipotong"
    }

    fun selectRegion(id: Int) {
        _selectedRegionId.value = id
        val trackId = _project.value.tracks.find { it.regions.any { r -> r.id == id } }?.id
        if (trackId != null) _selectedTrackId.value = trackId
    }

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
        // Delete WAV file from project directory if it exists
        val track = _project.value.tracks.find { it.id == trackId }
        if (track != null) {
            val wavName = track.name.replace(Regex("[^a-zA-Z0-9 _-]"), "_") + ".wav"
            val projectDir = File("/storage/emulated/0/Music/${_project.value.name.replace(Regex("[^a-zA-Z0-9 _-]"), "_")}.sbrk")
            val wavFile = File(projectDir, wavName)
            if (wavFile.exists()) wavFile.delete()
        }
        _trackPcmData.remove(trackId)
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

                    val msPerBar = getMsPerBar().toLong()
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
                for (track in _project.value.tracks) {
                    val pcmData = _trackPcmData[track.id]
                    val wavName = track.name.replace(Regex("[^a-zA-Z0-9 _-]"), "_") + ".wav"
                    if (pcmData != null && pcmData.isNotEmpty()) {
                        val wavFile = File(dir, wavName)
                        audioEngine.writeWavFile(pcmData, wavFile)
                    }

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
                        put("audioFile", if (pcmData != null && pcmData.isNotEmpty()) wavName else "")
                        put("inputSource", track.inputSource)
                        put("channels", track.channels)
                        put("bitDepth", track.bitDepth)
                        put("eqLow", track.eqLow.toDouble())
                        put("eqMid", track.eqMid.toDouble())
                        put("eqHigh", track.eqHigh.toDouble())
                        put("regions", regions)
                        put("effects", JSONArray().apply {
                            track.effects.forEach { fx ->
                                put(JSONObject().apply {
                                    put("id", fx.id)
                                    put("name", fx.name)
                                    put("icon", fx.icon)
                                    put("isEnabled", fx.isEnabled)
                                    put("params", JSONObject(fx.params.mapValues { it.value.toDouble() }))
                                })
                            }
                        })
                    })
                }

                val root = JSONObject().apply {
                    put("name", projectName)
                    put("bpm", _project.value.bpm)
                    put("isLooping", _project.value.isLooping)
                    put("isClickOn", _project.value.isClickOn)
                    put("tracks", JSONArray(trackDataList))
                    put("masterEq", JSONArray(_project.value.masterEq.map { it.toDouble() }))
                    put("masterEqPreset", _project.value.masterEqPreset)
                    put("masterEqEnabled", _project.value.masterEqEnabled)
                    val customPresetsObj = JSONObject()
                    _project.value.customPresets.forEach { (name, bands) ->
                        customPresetsObj.put(name, JSONArray(bands.map { it.toDouble() }))
                    }
                    put("customPresets", customPresetsObj)
                    val chordsArray = JSONArray()
                    _project.value.chordMarkers.forEach { marker ->
                        chordsArray.put(JSONObject().apply {
                            put("bar", marker.bar.toDouble())
                            put("chord", marker.chord)
                        })
                    }
                    put("chordMarkers", chordsArray)
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
                val masterEqPreset = root.optString("masterEqPreset", "Flat")
                val masterEqEnabled = root.optBoolean("masterEqEnabled", true)
                val customPresets = mutableMapOf<String, List<Float>>()
                val customPresetsJson = root.optJSONObject("customPresets")
                if (customPresetsJson != null) {
                    val keys = customPresetsJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val arr = customPresetsJson.getJSONArray(key)
                        val bands = (0 until arr.length()).map { arr.getDouble(it).toFloat() }
                        customPresets[key] = bands
                    }
                }
                val masterEqArray = root.optJSONArray("masterEq")
                val masterEq = if (masterEqArray != null) {
                    (0 until masterEqArray.length()).map { masterEqArray.getDouble(it).toFloat() }
                } else List(10) { 0f }
                val chordMarkers = mutableListOf<ChordMarker>()
                val chordsArray = root.optJSONArray("chordMarkers")
                if (chordsArray != null) {
                    for (i in 0 until chordsArray.length()) {
                        val obj = chordsArray.getJSONObject(i)
                        chordMarkers.add(ChordMarker(obj.getDouble("bar").toFloat(), obj.getString("chord")))
                    }
                }
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
                    val effectsList = mutableListOf<id.soundbreaker.studio.data.Effect>()
                    val effectsJson = t.optJSONArray("effects")
                    if (effectsJson != null) {
                        for (k in 0 until effectsJson.length()) {
                            val fx = effectsJson.getJSONObject(k)
                            val paramsJson = fx.optJSONObject("params")
                            val params = mutableMapOf<String, Float>()
                            if (paramsJson != null) {
                                for (key in paramsJson.keys()) {
                                    params[key] = paramsJson.getDouble(key).toFloat()
                                }
                            }
                            effectsList.add(id.soundbreaker.studio.data.Effect(
                                id = fx.getInt("id"),
                                name = fx.getString("name"),
                                icon = fx.optString("icon", "fx"),
                                isEnabled = fx.optBoolean("isEnabled", true),
                                params = params
                            ))
                        }
                    }

                    newTracks.add(Track(trackId, t.getString("name"), type, color,
                        t.getDouble("volume").toFloat(), t.getDouble("pan").toFloat(),
                        inputSource = t.optString("inputSource", "Mic"),
                        channels = t.optInt("channels", 2),
                        bitDepth = t.optInt("bitDepth", 16),
                        eqLow = t.optDouble("eqLow", 0.0).toFloat(),
                        eqMid = t.optDouble("eqMid", 0.0).toFloat(),
                        eqHigh = t.optDouble("eqHigh", 0.0).toFloat(),
                        regions = regions,
                        effects = effectsList))

                    var audioFileName = t.optString("audioFile", "")

                    // Fallback: if audioFile is empty, try to find a matching WAV by track name
                    if (audioFileName.isEmpty()) {
                        val trackName = t.getString("name")
                        val expectedWav = trackName.replace(Regex("[^a-zA-Z0-9 _-]"), "_") + ".wav"
                        val candidateFile = File(actualDir, expectedWav)
                        if (candidateFile.exists() && candidateFile.length() > 0) {
                            audioFileName = expectedWav
                            android.util.Log.e("SB", "Fallback: found $expectedWav for track $trackId (audioFile was empty)")
                        }
                    }

                    if (audioFileName.isNotEmpty()) {
                        var pcm: ShortArray? = null

                        // Method 1: Direct file path
                        val wavFile = File(actualDir, audioFileName)
                        if (wavFile.exists()) {
                            android.util.Log.e("SB", "File exists: ${wavFile.absolutePath}, size=${wavFile.length()}")
                            pcm = try { audioEngine.readWavFile(wavFile) } catch (e: OutOfMemoryError) {
                                android.util.Log.e("SB", "OOM loading $audioFileName for track $trackId")
                                null
                            } catch (e: Exception) {
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
                                pcm = try { audioEngine.readWavFile(sdcardFile) } catch (e: OutOfMemoryError) { null } catch (e: Exception) { null }
                            }
                        }

                        if (pcm != null && pcm.isNotEmpty()) {
                            newPcm[trackId] = pcm
                            // Fix: update track's audioFile in the JSON if it was empty
                            if (t.optString("audioFile", "").isEmpty()) {
                                t.put("audioFile", audioFileName)
                            }
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

                        _project.value = _project.value.copy(
                             name = name,
                             bpm = bpm,
                             isLooping = isLooping,
                             isClickOn = isClickOn,
                             tracks = tracksWithWaveform,
                             masterEq = masterEq,
                             masterEqPreset = masterEqPreset,
                             masterEqEnabled = masterEqEnabled,
                             customPresets = customPresets,
                             chordMarkers = chordMarkers,
                         )
                         audioEngine.setMasterEq(masterEq)
                         audioEngine.setMasterEqEnabled(masterEqEnabled)
                        _trackPcmData.clear()
                        _trackPcmData.putAll(newPcm)
                        regionIdCounter = tracksWithWaveform.maxOfOrNull { t -> t.regions.maxOfOrNull { it.id } ?: 0 } ?: 0
                        nextTrackId = (tracksWithWaveform.maxOfOrNull { it.id } ?: 0) + 1
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

    fun exportWav(fileName: String = "${_project.value.name}_export", folderPath: String = "/sdcard/Music", folderUri: android.net.Uri? = null) {
        if (_isExporting.value) return
        val project = _project.value
        val pcmData = _trackPcmData
        if (pcmData.isEmpty()) {
            _message.value = "Tidak ada audio untuk di-export"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isExporting.value = true
            _exportProgress.value = 0f
            try {
                val exporter = AudioExporter()
                val safeName = fileName.replace(Regex("[^a-zA-Z0-9 _-]"), "_")
                val config = AudioExporter.ExportConfig(
                    trackPcmData = pcmData,
                    trackIds = project.tracks.map { it.id },
                    volumes = getTrackVolumes(),
                    pans = getTrackPans(),
                    eq = getTrackEq(),
                    effects = getTrackEffects(),
                    bpm = project.bpm,
                    isClickOn = project.isClickOn,
                    masterEq = project.masterEq,
                    masterEqEnabled = project.masterEqEnabled,
                    masterVolume = audioEngine.getMasterVolume(),
                    masterPan = audioEngine.getMasterPan(),
                    beatsPerBar = getBeatsPerBar(),
                )

                val context = getApplication<Application>()
                val success = if (folderUri != null) {
                    // SAF: export to temp file, then create file in picked folder
                    val tempFile = File(context.cacheDir, "$safeName.wav")
                    val ok = exporter.exportToWav(config, tempFile) { _exportProgress.value = it }
                    if (ok) {
                        try {
                            // Convert tree URI to document URI for createDocument
                            val docUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(
                                folderUri, android.provider.DocumentsContract.getTreeDocumentId(folderUri)
                            )
                            val fileUri = android.provider.DocumentsContract.createDocument(
                                context.contentResolver, docUri, "audio/wav", "$safeName.wav"
                            )
                            if (fileUri != null) {
                                context.contentResolver.openOutputStream(fileUri)?.use { out ->
                                    tempFile.inputStream().use { input -> input.copyTo(out) }
                                }
                                true
                            } else false
                        } catch (e: Exception) {
                            android.util.Log.e("ViewModel", "SAF write failed: ${e.message}")
                            false
                        } finally {
                            tempFile.delete()
                        }
                    } else {
                        tempFile.delete()
                        false
                    }
                } else {
                    val dir = File(folderPath)
                    dir.mkdirs()
                    exporter.exportToWav(config, File(dir, "$safeName.wav")) { _exportProgress.value = it }
                }

                withContext(Dispatchers.Main) {
                    _message.value = if (success) "Exported: ${safeName}.wav" else "Export gagal"
                    _isExporting.value = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _message.value = "Export error: ${e.message}"
                    _isExporting.value = false
                }
            }
        }
    }

    private fun getLastBarPosition(): Float {
        var last = 1f
        for (track in _project.value.tracks) for (r in track.regions) {
            val end = r.startBar + r.widthBars
            if (end > last) last = end
        }
        return last
    }

    private fun startPlayheadTimer(startBar: Float = 1f) {
        stopPlayheadTimer()
        val msPerBar = getMsPerBar()
        val startOffsetMs = ((startBar - 1f) * msPerBar).toLong()
        recordStartTimeMs = System.currentTimeMillis() - startOffsetMs
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
                    // Grow recording region width from playhead
                    val elapsedWidth = (pos - recordStartBar).coerceAtLeast(0.5f)
                    val armedTrackId = _project.value.tracks.find { it.isArmed }?.id
                    if (armedTrackId != null) {
                        updateTrack(armedTrackId) { track ->
                            val regions = track.regions.map { r ->
                                if (r.name == "Recording...") r.copy(widthBars = elapsedWidth)
                                else r
                            }
                            track.copy(regions = regions)
                        }
                    }
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
