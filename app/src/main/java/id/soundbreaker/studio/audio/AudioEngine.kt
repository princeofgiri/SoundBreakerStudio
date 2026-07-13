package id.soundbreaker.studio.audio

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.content.Context
import kotlinx.coroutines.*
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList

class AudioEngine {

    companion object {
        const val SAMPLE_RATE = 44100
        const val CHANNELS = 2
        const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_STEREO
        const val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_STEREO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val BITS_PER_SAMPLE = 16
        const val BYTES_PER_SAMPLE = 2
    }

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isRecording = false
    private var isPlaying = false
    private var isPaused = false
    private var recordJob: Job? = null
    private var playJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val recordedBuffers = CopyOnWriteArrayList<ShortArray>()
    private var totalRecordedFrames = 0

    private data class PlaybackState(
        val buffers: List<ShortArray>,
        val volumes: List<Float>,
        val pans: List<Float>,
        val eq: List<Triple<Float, Float, Float>>,
        val effects: List<List<id.soundbreaker.studio.data.Effect>> = emptyList(),
        val bpm: Int = 120,
        val isClickOn: Boolean = false,
        val trackOffsets: List<Int> = emptyList(),
        val beatsPerBar: Int = 4,
    )

    @Volatile private var playbackState = PlaybackState(emptyList(), emptyList(), emptyList(), emptyList())
    @Volatile private var masterVolume = 0.78f
    @Volatile private var masterPan = 0.5f
    private var eqFilters: Array<Array<BiquadFilter>> = emptyArray()
    private val effectsChain = TrackEffectsChain(SAMPLE_RATE)
    private val masterEq = MasterEqProcessor(SAMPLE_RATE)
    private var isMasterEqEnabled = true
    private var preferredOutputDevice = "Speaker"
    private var preferredOutputDeviceInfo: AudioDeviceInfo? = null
    private var playbackPosition = 0

    var onAmplitude: ((Float) -> Unit)? = null
    var onRecordComplete: ((File) -> Unit)? = null
    var onRecordingWaveform: ((FloatArray) -> Unit)? = null
    var onPlaybackPosition: ((Int, Int) -> Unit)? = null
    var onPlaybackFinished: (() -> Unit)? = null
    var onTrackAmplitudes: ((List<Float>) -> Unit)? = null

    fun startRecording(outputFile: File, context: Context? = null, deviceName: String = "Mic"): Boolean {
        if (isRecording) return false

        try {
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT)
                .coerceAtLeast(SAMPLE_RATE * BYTES_PER_SAMPLE * CHANNELS)

            audioRecord = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG_IN)
                        .setEncoding(AUDIO_FORMAT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build()

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                android.util.Log.e("SB", "AudioRecord NOT initialized, state=${audioRecord?.state}")
                try { audioRecord?.release() } catch (_: Exception) {}
                audioRecord = null
                return false
            }

            // Route to specific device if not built-in mic
            if (deviceName != "Mic Internal" && context != null) {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                for (device in devices) {
                    val name = device.productName?.toString() ?: continue
                    if (deviceName.contains(name)) {
                        audioRecord?.setPreferredDevice(device)
                        break
                    }
                }
            }

            recordedBuffers.clear()
            totalRecordedFrames = 0
            isRecording = true

            audioRecord?.startRecording()

            recordJob = scope.launch {
                val buffer = ShortArray(bufferSize / BYTES_PER_SAMPLE)
                var chunkCount = 0
                var readErrors = 0

                while (isActive && isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        readErrors = 0
                        val copy = buffer.copyOf(read)
                        recordedBuffers.add(copy)
                        totalRecordedFrames += read / CHANNELS

                        val maxAmplitude = copy.maxOfOrNull { Math.abs(it.toInt()) } ?: 0
                        onAmplitude?.invoke(maxAmplitude / Short.MAX_VALUE.toFloat())

                        chunkCount++
                        if (chunkCount % 5 == 0) {
                            try {
                                val pcm = mergeBuffers()
                                if (pcm.isNotEmpty()) {
                                    val maxSamples = SAMPLE_RATE * CHANNELS * 5
                                    val startIdx = (pcm.size - maxSamples).coerceAtLeast(0)
                                    val recentPcm = if (startIdx > 0) pcm.copyOfRange(startIdx, pcm.size) else pcm
                                    val numPoints = ((recentPcm.size.toFloat() / CHANNELS / SAMPLE_RATE * 60 / 240) * 10).toInt().coerceIn(100, 400)
                                    onRecordingWaveform?.invoke(generatePeaksFromPcm(recentPcm, numPoints))
                                }
                            } catch (_: Exception) {}
                        }
                    } else {
                        readErrors++
                        if (readErrors > 50) {
                            android.util.Log.e("SB", "AudioRecord read failed $readErrors times, stopping")
                            break
                        }
                    }
                }

                try {
                    val pcm = mergeBuffers()
                    if (pcm.isNotEmpty()) {
                        onRecordingWaveform?.invoke(generatePeaksFromPcm(pcm, 200))
                    }
                } catch (_: Exception) {}

                try { audioRecord?.stop() } catch (_: Exception) {}
                try { audioRecord?.release() } catch (_: Exception) {}
                audioRecord = null
                isRecording = false

                val pcmData = mergeBuffers()
                writeWavFile(pcmData, outputFile)
                onRecordComplete?.invoke(outputFile)
            }

            return true
        } catch (e: Exception) {
            android.util.Log.e("SB", "startRecording FAILED: ${e.message}")
            try { audioRecord?.release() } catch (_: Exception) {}
            audioRecord = null
            isRecording = false
            return false
        }
    }

    fun stopRecording() {
        isRecording = false
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
        onRecordingWaveform = null
        recordJob?.cancel()
    }

    fun startPlaybackFromPosition(trackPcmData: List<ShortArray>, startFrame: Int, volumes: List<Float> = emptyList(), pans: List<Float> = emptyList(), eq: List<Triple<Float, Float, Float>> = emptyList(), effects: List<List<id.soundbreaker.studio.data.Effect>> = emptyList(), bpm: Int = 120, isClickOn: Boolean = false, trackOffsets: List<Int> = emptyList(), beatsPerBar: Int = 4) {
        if (isPlaying) stopPlayback()

        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT
        )
        val bufferSize = (minBuffer * 2).coerceAtLeast(8192)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG_OUT)
                    .setEncoding(AUDIO_FORMAT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        resolveOutputDevice()?.let { audioTrack?.setPreferredDevice(it) }

        playbackState = PlaybackState(
            buffers = trackPcmData,
            volumes = if (volumes.size == trackPcmData.size) volumes else trackPcmData.map { 1f },
            pans = if (pans.size == trackPcmData.size) pans else trackPcmData.map { 0.5f },
            eq = if (eq.size == trackPcmData.size) eq else trackPcmData.map { Triple(0f, 0f, 0f) },
            effects = if (effects.size == trackPcmData.size) effects else trackPcmData.map { emptyList() },
            bpm = bpm,
            isClickOn = isClickOn,
            trackOffsets = if (trackOffsets.size == trackPcmData.size) trackOffsets else trackPcmData.map { 0 },
            beatsPerBar = beatsPerBar,
        )
        initEqFilters()
        playbackPosition = startFrame.coerceAtLeast(0)
        isPlaying = true

        audioTrack?.play()

        playJob = scope.launch {
            val framesPerChunk = 2048
            val totalFramesFromPcm = trackPcmData.indices.maxOfOrNull { idx ->
                val pcmFrames = trackPcmData[idx].size / 2
                val offset = if (idx < trackOffsets.size) trackOffsets[idx] else 0
                pcmFrames + offset
            } ?: 0
            // If click is on but no audio, run for 5 minutes so click can play
            val totalFrames = if (totalFramesFromPcm == 0 && playbackState.isClickOn) {
                SAMPLE_RATE * 300 // 5 minutes
            } else totalFramesFromPcm

            while (isActive && isPlaying && playbackPosition < totalFrames) {
                if (isPaused) {
                    kotlinx.coroutines.delay(50)
                    continue
                }
                val state = playbackState
                val framesToRead = minOf(framesPerChunk, totalFrames - playbackPosition)
                val stereoOutput = mixMultipleTracks(state.buffers, playbackPosition, framesToRead, state.volumes, state.pans, state.trackOffsets)

                // Calculate per-track amplitude for level meters
                if (onTrackAmplitudes != null) {
                    val amps = FloatArray(state.buffers.size) { idx ->
                        val buf = state.buffers[idx]
                        val vol = if (idx < state.volumes.size) state.volumes[idx] else 1f
                        if (vol <= 0.001f || buf.isEmpty()) 0f
                        else {
                            var max = 0f
                            val checkFrames = minOf(framesToRead, 256)
                            for (f in 0 until checkFrames) {
                                val si = (playbackPosition + f) * 2
                                if (si + 1 < buf.size) {
                                    val l = Math.abs(buf[si].toInt()) / Short.MAX_VALUE.toFloat()
                                    val r = Math.abs(buf[si + 1].toInt()) / Short.MAX_VALUE.toFloat()
                                    val peak = maxOf(l, r) * vol
                                    if (peak > max) max = peak
                                }
                            }
                            max
                        }
                    }
                    onTrackAmplitudes?.invoke(amps.toList())
                }

                if (state.isClickOn && state.bpm > 0) {
                    mixClickTrack(stereoOutput, framesToRead, playbackPosition, state.bpm, state.beatsPerBar)
                }

                // Apply master EQ
                if (isMasterEqEnabled) {
                    masterEq.processStereo(stereoOutput, framesToRead)
                }

                // Apply master volume and pan
                val mv = masterVolume
                val mp = masterPan
                val mpLeft = kotlin.math.cos(mp.toDouble() * Math.PI / 2.0).toFloat()
                val mpRight = kotlin.math.sin(mp.toDouble() * Math.PI / 2.0).toFloat()
                for (j in 0 until stereoOutput.size step 2) {
                    val left = (stereoOutput[j].toFloat() * mv * mpLeft).toInt().toShort()
                    val right = (stereoOutput[j + 1].toFloat() * mv * mpRight).toInt().toShort()
                    stereoOutput[j] = left
                    stereoOutput[j + 1] = right
                }

                val written = try { audioTrack?.write(stereoOutput, 0, stereoOutput.size) } catch (_: Exception) { -1 }
                if (written != null && written > 0) {
                    playbackPosition += framesToRead
                    onPlaybackPosition?.invoke(playbackPosition, totalFrames)
                } else {
                    break
                }
            }

            try {
                audioTrack?.stop()
                audioTrack?.release()
            } catch (_: Exception) {}
            audioTrack = null
            isPlaying = false
            isPaused = false
            onPlaybackFinished?.invoke()
        }
    }

    fun startPlayback(trackPcmData: List<ShortArray>) {
        startPlaybackFromPosition(trackPcmData, 0)
    }

    fun startPlaybackSingle(pcmData: ShortArray) {
        startPlayback(listOf(pcmData))
    }

    fun startClickOnly(bpm: Int, beatsPerBar: Int, numBars: Int) {
        stopPlayback()

        val minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT)
        val bufferSize = (minBuffer * 2).coerceAtLeast(8192)
        val clickTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG_OUT)
                    .setEncoding(AUDIO_FORMAT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        preferredOutputDeviceInfo?.let { clickTrack.setPreferredDevice(it) }

        isPlaying = true
        clickTrack.play()

        playJob = scope.launch {
            val framesPerBeat = (SAMPLE_RATE.toLong() * 60 / bpm).toInt()
            val clickDuration = (SAMPLE_RATE * 0.005).toInt().coerceAtLeast(1)
            val totalFrames = framesPerBeat * beatsPerBar * numBars
            val buf = ShortArray(framesPerBeat * 2)

            for (beat in 0 until beatsPerBar * numBars) {
                buf.fill(0)
                // Generate click for this beat
                val freq = if (beat % beatsPerBar == 0) 1000f else 800f
                val clickFrames = minOf(clickDuration, framesPerBeat)
                for (i in 0 until clickFrames) {
                    val progress = i.toFloat() / clickDuration
                    val envelope = (1f - progress) * 0.5f
                    val sample = (Math.sin(2.0 * Math.PI * freq * i.toDouble() / SAMPLE_RATE) * Short.MAX_VALUE * envelope).toInt().toShort()
                    buf[i * 2] = sample
                    buf[i * 2 + 1] = sample
                }
                // Fill rest with silence
                for (i in clickFrames until framesPerBeat) {
                    buf[i * 2] = 0
                    buf[i * 2 + 1] = 0
                }
                clickTrack.write(buf, 0, buf.size)
            }

            try { clickTrack.stop() } catch (_: Exception) {}
            try { clickTrack.release() } catch (_: Exception) {}
            isPlaying = false
        }
    }

    fun stopPlayback() {
        isPlaying = false
        playJob?.cancel()
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }

    fun pausePlayback() {
        isPaused = true
    }

    fun resumePlayback() {
        isPaused = false
    }

    fun seekPlayback(position: Int) {
        playbackPosition = position.coerceAtLeast(0)
    }

    fun updatePlaybackBuffers(newBuffers: List<ShortArray>, volumes: List<Float> = emptyList(), pans: List<Float> = emptyList(), eq: List<Triple<Float, Float, Float>> = emptyList(), effects: List<List<id.soundbreaker.studio.data.Effect>> = emptyList(), bpm: Int = 120, isClickOn: Boolean = false) {
        val current = playbackState
        playbackState = current.copy(
            buffers = newBuffers,
            volumes = if (volumes.size == newBuffers.size) volumes else current.volumes,
            pans = if (pans.size == newBuffers.size) pans else current.pans,
            eq = if (eq.size == newBuffers.size) eq else current.eq,
            effects = if (effects.size == newBuffers.size) effects else current.effects,
            bpm = bpm,
            isClickOn = isClickOn,
        )
        if (eq.size == newBuffers.size) {
            initEqFilters()
        }
    }

    private fun initEqFilters() {
        val eq = playbackState.eq
        eqFilters = Array(eq.size) { trackIdx ->
            val (low, mid, high) = eq.getOrElse(trackIdx) { Triple(0f, 0f, 0f) }
            val sr = SAMPLE_RATE.toFloat()
            arrayOf(
                BiquadFilter().apply { configureLowShelf(200f, low, 0.707f, sr) },
                BiquadFilter().apply { configurePeaking(1000f, mid, 1.0f, sr) },
                BiquadFilter().apply { configureHighShelf(4000f, high, 0.707f, sr) },
            )
        }
    }

    fun isRecording() = isRecording
    fun isPlaying() = isPlaying

    fun setMasterVolume(volume: Float) {
        masterVolume = volume
    }

    fun getMasterVolume(): Float = masterVolume

    fun setMasterPan(pan: Float) {
        masterPan = pan
    }

    fun getMasterPan(): Float = masterPan

    fun setMasterEq(bands: List<Float>) {
        masterEq.setGains(bands)
    }

    fun setMasterEqEnabled(enabled: Boolean) {
        isMasterEqEnabled = enabled
    }

    fun getAvailableInputs(context: Context): List<String> {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val inputs = mutableListOf("Mic Internal")
        for (device in devices) {
            val name = device.productName?.toString() ?: ""
            when (device.type) {
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_ACCESSORY,
                AudioDeviceInfo.TYPE_USB_HEADSET -> {
                    val label = if (name.isNotBlank()) name else "USB Audio"
                    val channels = device.channelCounts
                    if (channels != null && channels.size > 1 && channels.max() >= 2) {
                        inputs.add("USB Input 1 ($label, Ch 1)")
                        inputs.add("USB Input 2 ($label, Ch 2)")
                    } else {
                        inputs.add("USB Input 1 ($label)")
                    }
                }
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                    val label = if (name.isNotBlank()) name else "BT"
                    inputs.add("Mic BT ($label)")
                }
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> {
                    val label = if (name.isNotBlank()) name else "BT"
                    inputs.add("Mic BT ($label)")
                }
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> { /* already have "Mic Internal" */ }
            }
        }
        return inputs.distinct()
    }

    fun getAvailableOutputs(context: Context): List<String> {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val outputs = mutableListOf("Speaker")
        for (device in devices) {
            val name = device.productName?.toString() ?: continue
            if (name.isBlank()) continue
            when (device.type) {
                AudioDeviceInfo.TYPE_USB_DEVICE -> outputs.add("USB: $name")
                AudioDeviceInfo.TYPE_USB_ACCESSORY -> outputs.add("USB: $name")
                AudioDeviceInfo.TYPE_USB_HEADSET -> outputs.add("USB: $name")
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> outputs.add("BT: $name")
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> outputs.add("BT: $name")
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> outputs.add("Headset: $name")
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> outputs.add("Headphones: $name")
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> { /* already have "Speaker" */ }
            }
        }
        return outputs.distinct()
    }

    fun setOutputDevice(context: Context, deviceName: String) {
        this.context = context
        preferredOutputDevice = deviceName
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        if (deviceName == "Speaker") {
            preferredOutputDeviceInfo = null
            for (device in devices) {
                if (device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    preferredOutputDeviceInfo = device
                    break
                }
            }
            // If currently playing, need to restart with new device routing
            restartWithCurrentState()
            return
        }

        for (device in devices) {
            val name = device.productName?.toString() ?: continue
            if (deviceName.contains(name)) {
                preferredOutputDeviceInfo = device
                restartWithCurrentState()
                break
            }
        }
    }

    private var context: Context? = null

    private fun resolveOutputDevice(): AudioDeviceInfo? {
        if (preferredOutputDevice == "Speaker") return preferredOutputDeviceInfo
        val ctx = context ?: return preferredOutputDeviceInfo
        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        for (device in devices) {
            val name = device.productName?.toString() ?: continue
            if (preferredOutputDevice.contains(name) || name.contains(preferredOutputDevice)) {
                return device
            }
        }
        return preferredOutputDeviceInfo
    }

    private fun restartWithCurrentState() {
        if (!isPlaying) return
        val state = playbackState
        val pos = playbackPosition
        // Stop current playback
        try { audioTrack?.stop() } catch (_: Exception) {}
        try { audioTrack?.release() } catch (_: Exception) {}
        audioTrack = null
        isPlaying = false
        isPaused = false
        // Rebuild AudioTrack with new device routing
        val minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT)
        val bufferSize = (minBuffer * 2).coerceAtLeast(8192)
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG_OUT)
                    .setEncoding(AUDIO_FORMAT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        resolveOutputDevice()?.let { audioTrack?.setPreferredDevice(it) }
        playbackPosition = pos
        isPlaying = true
        audioTrack?.play()
        playJob?.cancel()
        playJob = scope.launch {
            val framesPerChunk = 2048
            val totalFrames = state.buffers.indices.maxOfOrNull { idx ->
                val pcmFrames = state.buffers[idx].size / 2
                val offset = if (idx < state.trackOffsets.size) state.trackOffsets[idx] else 0
                pcmFrames + offset
            } ?: 0
            while (isActive && isPlaying && playbackPosition < totalFrames) {
                if (isPaused) { kotlinx.coroutines.delay(50); continue }
                val st = playbackState
                val framesToRead = minOf(framesPerChunk, totalFrames - playbackPosition)
                val stereoOutput = mixMultipleTracks(st.buffers, playbackPosition, framesToRead, st.volumes, st.pans, st.trackOffsets)
                if (st.isClickOn && st.bpm > 0) mixClickTrack(stereoOutput, framesToRead, playbackPosition, st.bpm, st.beatsPerBar)
                val mv = masterVolume; val mp = masterPan
                val mpLeft = kotlin.math.cos(mp.toDouble() * Math.PI / 2.0).toFloat()
                val mpRight = kotlin.math.sin(mp.toDouble() * Math.PI / 2.0).toFloat()
                for (j in 0 until stereoOutput.size step 2) {
                    stereoOutput[j] = (stereoOutput[j].toFloat() * mv * mpLeft).toInt().toShort()
                    stereoOutput[j + 1] = (stereoOutput[j + 1].toFloat() * mv * mpRight).toInt().toShort()
                }
                val written = try { audioTrack?.write(stereoOutput, 0, stereoOutput.size) } catch (_: Exception) { -1 }
                if (written != null && written > 0) {
                    playbackPosition += framesToRead
                    onPlaybackPosition?.invoke(playbackPosition, totalFrames)
                } else break
            }
            try { audioTrack?.stop(); audioTrack?.release() } catch (_: Exception) {}
            audioTrack = null; isPlaying = false; isPaused = false
            onPlaybackFinished?.invoke()
        }
    }

    private fun mixMultipleTracks(
        tracks: List<ShortArray>,
        startFrame: Int,
        maxFrames: Int,
        volumes: List<Float> = emptyList(),
        pans: List<Float> = emptyList(),
        trackOffsets: List<Int> = emptyList(),
    ): ShortArray {
        val stereoOutput = ShortArray(maxFrames * 2)
        val hasEq = eqFilters.isNotEmpty()

        for (i in 0 until maxFrames) {
            var leftSum = 0L
            var rightSum = 0L
            for ((idx, track) in tracks.withIndex()) {
                val vol = if (idx < volumes.size) volumes[idx] else 1f
                if (vol <= 0.001f) continue
                val pan = if (idx < pans.size) pans[idx] else 0.5f
                val panAngle = pan * Math.PI.toFloat() / 2f
                val leftGain = Math.cos(panAngle.toDouble()).toFloat()
                val rightGain = Math.sin(panAngle.toDouble()).toFloat()
                val offset = if (idx < trackOffsets.size) trackOffsets[idx] else 0
                val sampleIdx = (startFrame + i - offset) * 2
                if (sampleIdx >= 0 && sampleIdx + 1 < track.size) {
                    var left = track[sampleIdx].toFloat() * vol
                    var right = track[sampleIdx + 1].toFloat() * vol
                    if (hasEq && idx < eqFilters.size) {
                        val filters = eqFilters[idx]
                        left = filters[0].process(left)
                        left = filters[1].process(left)
                        left = filters[2].process(left)
                        right = filters[0].process(right)
                        right = filters[1].process(right)
                        right = filters[2].process(right)
                    }
                    val state = playbackState
                    if (state.effects.isNotEmpty() && idx < state.effects.size) {
                        val trackFx = state.effects[idx]
                        if (trackFx.isNotEmpty()) {
                            left = effectsChain.processTrack(idx, left, trackFx)
                            right = effectsChain.processTrack(idx + 10000, right, trackFx)
                        }
                    }
                    leftSum += (left * leftGain).toLong()
                    rightSum += (right * rightGain).toLong()
                }
            }
            stereoOutput[i * 2] = leftSum.coerceIn(Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong()).toShort()
            stereoOutput[i * 2 + 1] = rightSum.coerceIn(Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong()).toShort()
        }
        return stereoOutput
    }

    private fun mergeBuffers(): ShortArray {
        val totalSize = recordedBuffers.sumOf { it.size }
        val merged = ShortArray(totalSize)
        var offset = 0
        for (buffer in recordedBuffers) {
            System.arraycopy(buffer, 0, merged, offset, buffer.size)
            offset += buffer.size
        }
        return merged
    }

    fun writeWavFile(pcmData: ShortArray, outputFile: File) {
        android.util.Log.e("SoundBreaker", "writeWav: pcmData.size=${pcmData.size}, first10=${pcmData.take(10).toList()}")
        val baos = java.io.ByteArrayOutputStream()
        writeWavToStream(pcmData, baos)
        outputFile.writeBytes(baos.toByteArray())
    }

    fun writeWavToStream(pcmData: ShortArray, out: java.io.OutputStream) {
        val dataSize = pcmData.size * BYTES_PER_SAMPLE
        val fileSize = 36 + dataSize

        out.write("RIFF".toByteArray())
        out.write(intToBytes(fileSize))
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        out.write(intToBytes(16))
        out.write(shortToBytes(1.toShort()))
        out.write(shortToBytes(CHANNELS.toShort()))
        out.write(intToBytes(SAMPLE_RATE))
        out.write(intToBytes(SAMPLE_RATE * BYTES_PER_SAMPLE * CHANNELS))
        out.write(shortToBytes((BYTES_PER_SAMPLE * CHANNELS).toShort()))
        out.write(shortToBytes(BITS_PER_SAMPLE.toShort()))
        out.write("data".toByteArray())
        out.write(intToBytes(dataSize))

        val byteBuffer = java.nio.ByteBuffer.allocate(dataSize).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        pcmData.forEach { byteBuffer.putShort(it) }
        out.write(byteBuffer.array())
    }

    private fun scanWavChunks(fis: java.io.FileInputStream): Triple<Int, Int, Int>? {
        val buf = ByteBuffer.wrap(ByteArray(4)).order(ByteOrder.LITTLE_ENDIAN)
        var channels = 0
        var fileSampleRate = 0
        var dataSize = 0
        var foundFmt = false
        var foundData = false

        // Skip RIFF(4) + fileSize(4) + WAVE(4) = 12 bytes
        fis.skip(12)

        while (!foundData) {
            val chunkIdBytes = ByteArray(4)
            if (fis.read(chunkIdBytes) < 4) break
            val chunkId = String(chunkIdBytes)

            buf.clear()
            if (fis.read(buf.array()) < 4) break
            val chunkSize = buf.getInt(0)

            if (chunkId == "fmt ") {
                val fmtData = ByteArray(minOf(chunkSize, 40))
                fis.read(fmtData)
                val fmtBuf = ByteBuffer.wrap(fmtData).order(ByteOrder.LITTLE_ENDIAN)
                val audioFormat = fmtBuf.getShort(0).toInt()
                channels = fmtBuf.getShort(2).toInt().coerceIn(1, 2)
                fileSampleRate = fmtBuf.getInt(4).toInt().coerceIn(8000, 192000)
                foundFmt = true
                // Skip remaining fmt bytes
                val remaining = chunkSize - minOf(chunkSize, 40)
                if (remaining > 0) fis.skip(remaining.toLong())
            } else if (chunkId == "data") {
                dataSize = chunkSize
                foundData = true
            } else {
                // Skip unknown chunk
                fis.skip(chunkSize.toLong())
            }
        }

        if (!foundFmt || !foundData || channels == 0 || fileSampleRate == 0) return null
        return Triple(channels, fileSampleRate, dataSize)
    }

    fun readWavFile(file: File): ShortArray? {
        try {
            val fis = java.io.FileInputStream(file)
            val info = scanWavChunks(fis) ?: run { fis.close(); return null }
            val (channels, fileSampleRate, dataSize) = info
            val frameSize = channels * 2
            val totalFrames = dataSize / frameSize

            val pcmData = ShortArray(totalFrames * channels)
            val byteBuffer = ByteArray(8192)
            var shortsRead = 0
            val bb = ByteBuffer.wrap(byteBuffer).order(ByteOrder.LITTLE_ENDIAN)

            while (shortsRead < pcmData.size) {
                bb.clear()
                val toRead = minOf(byteBuffer.size, (pcmData.size - shortsRead) * 2)
                val bytesRead = fis.read(byteBuffer, 0, toRead)
                if (bytesRead <= 0) break
                val shortsInChunk = bytesRead / 2
                for (i in 0 until shortsInChunk) {
                    if (shortsRead < pcmData.size) {
                        pcmData[shortsRead++] = bb.getShort(i * 2)
                    }
                }
            }
            fis.close()
            val result = if (fileSampleRate != SAMPLE_RATE) {
                resample(pcmData, channels, fileSampleRate, SAMPLE_RATE)
            } else pcmData
            android.util.Log.i("SoundBreaker", "readWav OK: ch=$channels, sr=$fileSampleRate, frames=$totalFrames, size=${result.size}")
            return result
        } catch (e: Exception) {
            android.util.Log.e("SoundBreaker", "readWav FAILED: ${e.message}")
            return null
        }
    }

    fun readAudioFile(uri: android.net.Uri, context: android.content.Context): Triple<ShortArray, Int, String>? {
        try {
            android.util.Log.e("SoundBreaker", "Importing: $uri")

            var pcmData: ShortArray
            var channels: Int
            var fileSampleRate: Int

            // Try MediaCodec first
            val mcResult = try {
                decodeWithMediaCodec(uri, context)
            } catch (e: OutOfMemoryError) {
                android.util.Log.e("SoundBreaker", "OOM in MediaCodec")
                null
            } catch (e: Exception) {
                android.util.Log.e("SoundBreaker", "MediaCodec failed: ${e.message}")
                null
            }

            if (mcResult != null) {
                pcmData = mcResult.first
                channels = mcResult.second
                fileSampleRate = extractMediaCodecSampleRate(uri, context).coerceIn(8000, 96000)
            } else {
                // Manual WAV/AIFF parser (includes sample rate)
                android.util.Log.e("SoundBreaker", "MediaCodec failed, trying manual parser")
                val manualResult = tryManualParse(uri, context) ?: run {
                    android.util.Log.e("SoundBreaker", "All decode methods failed")
                    return null
                }
                pcmData = manualResult.first
                channels = manualResult.second
                fileSampleRate = manualResult.third
            }

            android.util.Log.e("SoundBreaker", "Decoded: ${pcmData.size} samples, $channels ch, sr=$fileSampleRate")

            if (pcmData.isEmpty()) return null

            val message = if (fileSampleRate != SAMPLE_RATE) {
                "Resample $fileSampleRate Hz → $SAMPLE_RATE Hz"
            } else "OK"

            if (fileSampleRate != SAMPLE_RATE) {
                pcmData = resample(pcmData, channels, fileSampleRate, SAMPLE_RATE)
            }

            return Triple(pcmData, channels, message)
        } catch (e: OutOfMemoryError) {
            android.util.Log.e("SoundBreaker", "OOM in readAudioFile")
            return null
        } catch (e: Exception) {
            android.util.Log.e("SoundBreaker", "readAudioFile error", e)
            return null
        }
    }

    private fun tryManualParse(uri: android.net.Uri, context: android.content.Context): Triple<ShortArray, Int, Int>? {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val headerBuf = ByteArray(12)
            val read = inputStream.read(headerBuf)
            inputStream.close()

            if (read < 4) return null
            val header = String(headerBuf, 0, 4)

            if (header == "RIFF") {
                return parseWavStream(uri, context)
            }

            if (header == "FORM") {
                return parseAiffStream(uri, context)
            }

            return null
        } catch (e: Exception) {
            android.util.Log.e("SoundBreaker", "tryManualParse error: ${e.message}")
            return null
        }
    }

    private fun parseWavStream(uri: android.net.Uri, context: android.content.Context): Triple<ShortArray, Int, Int>? {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null

            // Read enough for RIFF+WAVE header + scan for chunks
            val headerBuf = ByteArray(12)
            inputStream.read(headerBuf)
            val chunkId = String(headerBuf, 0, 4)
            if (chunkId != "RIFF") { inputStream.close(); return null }

            var channels = 0
            var fileSampleRate = 0
            var dataSize = 0
            var foundFmt = false
            var foundData = false
            val buf = ByteBuffer.wrap(ByteArray(4)).order(ByteOrder.LITTLE_ENDIAN)

            while (!foundData) {
                val idBytes = ByteArray(4)
                if (inputStream.read(idBytes) < 4) break
                val id = String(idBytes)
                buf.clear()
                if (inputStream.read(buf.array()) < 4) break
                val chunkSize = buf.getInt(0)

                if (id == "fmt ") {
                    val fmtData = ByteArray(minOf(chunkSize, 40))
                    inputStream.read(fmtData)
                    val fmtBuf = ByteBuffer.wrap(fmtData).order(ByteOrder.LITTLE_ENDIAN)
                    channels = fmtBuf.getShort(2).toInt().coerceIn(1, 2)
                    fileSampleRate = fmtBuf.getInt(4).toInt().coerceIn(8000, 192000)
                    foundFmt = true
                    val remaining = chunkSize - minOf(chunkSize, 40)
                    if (remaining > 0) inputStream.skip(remaining.toLong())
                } else if (id == "data") {
                    dataSize = chunkSize
                    foundData = true
                } else {
                    inputStream.skip(chunkSize.toLong())
                }
            }

            if (!foundFmt || !foundData || channels == 0 || fileSampleRate == 0) {
                inputStream.close(); return null
            }

            android.util.Log.e("SoundBreaker", "WAV parsed: sr=$fileSampleRate, ch=$channels, dataSize=$dataSize")

            val chunkSize = 65536
            val buffers = mutableListOf<ShortArray>()
            var totalRead = 0

            while (totalRead < dataSize) {
                val toRead = minOf(chunkSize, dataSize - totalRead)
                val chunkBuf = ByteArray(toRead)
                val read = inputStream.read(chunkBuf)
                if (read <= 0) break

                val shorts = ShortArray(read / 2)
                val bb = ByteBuffer.wrap(chunkBuf, 0, read).order(ByteOrder.LITTLE_ENDIAN)
                for (i in shorts.indices) {
                    shorts[i] = bb.short
                }
                buffers.add(shorts)
                totalRead += read
            }
            inputStream.close()

            val totalSamples = buffers.sumOf { it.size }
            val pcmData = ShortArray(totalSamples)
            var offset = 0
            for (buf_ in buffers) {
                System.arraycopy(buf_, 0, pcmData, offset, buf_.size)
                offset += buf_.size
            }

            android.util.Log.e("SoundBreaker", "WAV streamed: ${pcmData.size} samples, sr=$fileSampleRate")
            return Triple(pcmData, channels, fileSampleRate)
        } catch (e: Exception) {
            android.util.Log.e("SoundBreaker", "parseWavStream error: ${e.message}")
            return null
        }
    }

    private fun parseAiffStream(uri: android.net.Uri, context: android.content.Context): Triple<ShortArray, Int, Int>? {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val allBytes = inputStream.readBytes()
            inputStream.close()

            val result = parseAiff(allBytes) ?: return null
            val fileSampleRate = extractAiffSampleRate(allBytes)

            android.util.Log.e("SoundBreaker", "AIFF: ${result.first.size} samples, ${result.second} ch, sr=$fileSampleRate")
            return Triple(result.first, result.second, fileSampleRate)
        } catch (e: OutOfMemoryError) {
            android.util.Log.e("SoundBreaker", "OOM reading AIFF")
            return null
        } catch (e: Exception) {
            android.util.Log.e("SoundBreaker", "parseAiffStream error: ${e.message}")
            return null
        }
    }

    private fun resample(data: ShortArray, channels: Int, fromRate: Int, toRate: Int): ShortArray {
        val ratio = fromRate.toDouble() / toRate
        val outFrames = (data.size / channels / ratio).toInt()
        val output = ShortArray(outFrames * channels)

        for (i in 0 until outFrames) {
            val srcPos = i * ratio
            val srcIdx = srcPos.toInt().coerceIn(0, (data.size / channels) - 1)
            val fraction = (srcPos - srcIdx).toFloat()

            for (ch in 0 until channels) {
                val s0 = data[srcIdx * channels + ch].toFloat()
                val s1 = if (srcIdx + 1 < data.size / channels) {
                    data[(srcIdx + 1) * channels + ch].toFloat()
                } else s0
                output[i * channels + ch] = (s0 + (s1 - s0) * fraction).toInt().toShort()
            }
        }
        return output
    }

    private fun extractAiffSampleRate(bytes: ByteArray): Int {
        try {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            var offset = 12
            while (offset < bytes.size - 8) {
                val chunkId = String(bytes, offset, 4)
                val chunkSize = buffer.getInt(offset + 4)
                if (chunkId == "COMM") {
                    // Sample rate is 80-bit IEEE at offset+16, decode to int
                    val srHigh = buffer.getInt(offset + 16)
                    val srBytes = ByteArray(10)
                    buffer.position(offset + 16)
                    buffer.get(srBytes)
                    // Decode 80-bit IEEE extended precision
                    val sign = (srBytes[0].toInt() and 0x80) shr 7
                    val exponent = ((srBytes[0].toInt() and 0x7F) shl 8 or (srBytes[1].toInt() and 0xFF)) - 16383
                    val mantissa = (srBytes[2].toLong() shl 56) or
                            (srBytes[3].toLong() and 0xFF shl 48) or
                            (srBytes[4].toLong() and 0xFF shl 40) or
                            (srBytes[5].toLong() and 0xFF shl 32) or
                            (srBytes[6].toLong() and 0xFF shl 24) or
                            (srBytes[7].toLong() and 0xFF shl 16) or
                            (srBytes[8].toLong() and 0xFF shl 8) or
                            (srBytes[9].toLong() and 0xFF)
                    val value = (1.0 + mantissa.toDouble() / (1L shl 63)) * Math.pow(2.0, exponent.toDouble())
                    android.util.Log.e("SoundBreaker", "AIFF sample rate: $value")
                    return value.toInt()
                }
                offset += 8 + chunkSize
                if (chunkSize % 2 != 0) offset++
            }
        } catch (e: Exception) {
            android.util.Log.e("SoundBreaker", "extractAiffSampleRate error", e)
        }
        return SAMPLE_RATE
    }

    private fun extractMediaCodecSampleRate(uri: android.net.Uri, context: android.content.Context): Int {
        try {
            val extractor = android.media.MediaExtractor()
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    val sr = format.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
                    extractor.release()
                    return sr
                }
            }
            extractor.release()
        } catch (e: Exception) {}
        return SAMPLE_RATE
    }

    private fun parseAiff(bytes: ByteArray): Pair<ShortArray, Int>? {
        try {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

            var offset = 12
            var channels = 0
            var numFrames = 0
            var bitsPerSample = 0

            while (offset < bytes.size - 8) {
                val chunkId = String(bytes, offset, 4)
                val chunkSize = buffer.getInt(offset + 4)

                android.util.Log.e("SoundBreaker", "AIFF chunk: '$chunkId', size: $chunkSize at offset $offset")

                if (chunkId == "COMM") {
                    channels = buffer.getShort(offset + 8).toInt()
                    numFrames = buffer.getInt(offset + 10)
                    bitsPerSample = buffer.getShort(offset + 14).toInt()
                    android.util.Log.e("SoundBreaker", "COMM: ch=$channels, frames=$numFrames, bits=$bitsPerSample")
                    offset += 8 + chunkSize
                } else if (chunkId == "SSND") {
                    val dataOffset = offset + 16 // SSND header(8) + offset(4) + blockSize(4)

                    android.util.Log.e("SoundBreaker", "SSND found at offset $dataOffset, bits=$bitsPerSample")

                    val pcmData = ShortArray(numFrames * channels)

                    when (bitsPerSample) {
                        16 -> {
                            for (i in pcmData.indices) {
                                val pos = dataOffset + i * 2
                                if (pos + 2 <= bytes.size) {
                                    pcmData[i] = buffer.getShort(pos)
                                }
                            }
                        }
                        24 -> {
                            for (i in pcmData.indices) {
                                val pos = dataOffset + i * 3
                                if (pos + 3 <= bytes.size) {
                                    val b0 = bytes[pos].toInt() and 0xFF
                                    val b1 = bytes[pos + 1].toInt() and 0xFF
                                    val b2 = bytes[pos + 2].toInt() and 0xFF
                                    val sample24 = (b0 shl 16) or (b1 shl 8) or b2
                                    pcmData[i] = (sample24 shr 8).toShort()
                                }
                            }
                        }
                        32 -> {
                            for (i in pcmData.indices) {
                                val pos = dataOffset + i * 4
                                if (pos + 4 <= bytes.size) {
                                    pcmData[i] = (buffer.getInt(pos) shr 16).toShort()
                                }
                            }
                        }
                    }

                    android.util.Log.e("SoundBreaker", "AIFF parsed: ${pcmData.size} samples, $channels ch")
                    return Pair(pcmData, channels.coerceIn(1, 2))
                } else {
                    offset += 8 + chunkSize
                    if (chunkSize % 2 != 0) offset++
                }
            }
            android.util.Log.e("SoundBreaker", "AIFF: no SSND chunk found")
            return null
        } catch (e: Exception) {
            android.util.Log.e("SoundBreaker", "AIFF parse error", e)
            return null
        }
    }

    private fun decodeWithMediaCodec(uri: android.net.Uri, context: android.content.Context): Pair<ShortArray, Int>? {
        try {
            val extractor = android.media.MediaExtractor()
            extractor.setDataSource(context, uri, null)

            android.util.Log.e("SoundBreaker", "MediaExtractor tracks: ${extractor.trackCount}")

            var audioTrackIndex = -1
            var mime = ""
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                android.util.Log.e("SoundBreaker", "Track $i: mime=$mime")
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    break
                }
            }
            if (audioTrackIndex < 0) {
                android.util.Log.e("SoundBreaker", "No audio track found")
                return null
            }

            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)
            val sampleRate = format.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)

            android.util.Log.e("SoundBreaker", "Audio format: mime=$mime, sr=$sampleRate, ch=$channelCount")

            val codec = android.media.MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcmChunks = mutableListOf<ShortArray>()
            val bufferInfo = android.media.MediaCodec.BufferInfo()
            var isEos = false

            while (!isEos) {
                val inputIndex = codec.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex) ?: continue
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEos = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outputIndex >= 0) {
                    if (bufferInfo.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        codec.releaseOutputBuffer(outputIndex, false)
                        break
                    }
                    val outputBuffer = codec.getOutputBuffer(outputIndex) ?: continue
                    val shortArray = ShortArray(bufferInfo.size / 2)
                    outputBuffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.asShortBuffer().get(shortArray)
                    pcmChunks.add(shortArray)
                    codec.releaseOutputBuffer(outputIndex, false)
                }
            }

            codec.stop()
            codec.release()
            extractor.release()

            val totalSize = pcmChunks.sumOf { it.size }
            val merged = ShortArray(totalSize)
            var offset = 0
            for (chunk in pcmChunks) {
                System.arraycopy(chunk, 0, merged, offset, chunk.size)
                offset += chunk.size
            }

            android.util.Log.e("SoundBreaker", "Decoded: ${merged.size} samples")
            return Pair(merged, channelCount)
        } catch (e: Exception) {
            android.util.Log.e("SoundBreaker", "decodeWithMediaCodec error: ${e.message}", e)
            return null
        }
    }

    fun getRecordedPcm(): ShortArray = mergeBuffers()
    fun getRecordedFrameCount(): Int = totalRecordedFrames
    fun getTotalRecordedFrames(): Int = totalRecordedFrames
    fun getDurationMs(): Int = (totalRecordedFrames.toLong() * 1000 / SAMPLE_RATE).toInt()

    private fun intToBytes(value: Int): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    }

    private fun shortToBytes(value: Short): ByteArray {
        return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array()
    }

    fun generateWaveformFromRegion(pcmData: ShortArray, channels: Int, startBar: Float, widthBars: Float, totalBars: Int, numPoints: Int = 200): FloatArray {
        val totalFrames = pcmData.size / channels
        val framesPerBar = totalFrames.toFloat() / totalBars
        val startFrame = ((startBar - 1f) * framesPerBar).toInt().coerceIn(0, totalFrames)
        val endFrame = ((startBar - 1f + widthBars) * framesPerBar).toInt().coerceIn(0, totalFrames)
        val regionFrames = (endFrame - startFrame).coerceAtLeast(1)
        val framesPerPoint = regionFrames / numPoints
        if (framesPerPoint <= 0) return FloatArray(0)

        return FloatArray(numPoints) { i ->
            val frameStart = startFrame + i * framesPerPoint
            val frameEnd = minOf(frameStart + framesPerPoint, totalFrames)
            var max = 0f
            for (j in frameStart until frameEnd) {
                val sampleIdx = j * channels
                if (sampleIdx + 1 < pcmData.size) {
                    val left = Math.abs(pcmData[sampleIdx].toInt()) / Short.MAX_VALUE.toFloat()
                    val right = Math.abs(pcmData[sampleIdx + 1].toInt()) / Short.MAX_VALUE.toFloat()
                    val amp = maxOf(left, right)
                    if (amp > max) max = amp
                } else if (sampleIdx < pcmData.size) {
                    val amp = Math.abs(pcmData[sampleIdx].toInt()) / Short.MAX_VALUE.toFloat()
                    if (amp > max) max = amp
                }
            }
            max
        }
    }

    private fun generatePeaksFromPcm(pcm: ShortArray, numPoints: Int): FloatArray {
        val totalSamples = pcm.size
        val samplesPerPoint = (totalSamples / numPoints).coerceAtLeast(1)
        return FloatArray(numPoints) { i ->
            val start = i * samplesPerPoint
            val end = minOf(start + samplesPerPoint, totalSamples)
            var max = 0f
            for (j in start until end) {
                val amp = Math.abs(pcm[j].toInt()) / Short.MAX_VALUE.toFloat()
                if (amp > max) max = amp
            }
            max
        }
    }

    private fun mixClickTrack(output: ShortArray, frames: Int, startFrame: Int, bpm: Int, beatsPerBar: Int = 4) {
        val framesPerBeat = (SAMPLE_RATE.toLong() * 60 / bpm).toInt()
        val clickDuration = (SAMPLE_RATE * 0.005).toInt().coerceAtLeast(1) // 5ms click
        val clickFreqDown = 1000f
        val clickFreqUp = 800f

        for (i in 0 until frames) {
            val framePos = startFrame + i
            val posInBeat = framePos % framesPerBeat
            if (posInBeat < clickDuration) {
                val freq = if (framePos / framesPerBeat % beatsPerBar == 0) clickFreqDown else clickFreqUp
                val progress = posInBeat.toFloat() / clickDuration
                val envelope = (1f - progress) * 0.5f
                val sample = (Math.sin(2.0 * Math.PI * freq * posInBeat / SAMPLE_RATE) * Short.MAX_VALUE * envelope).toInt().toShort()
                val idx = i * 2
                if (idx + 1 < output.size) {
                    val left = output[idx].toLong() + sample.toLong()
                    val right = output[idx + 1].toLong() + sample.toLong()
                    output[idx] = left.coerceIn(Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong()).toShort()
                    output[idx + 1] = right.coerceIn(Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong()).toShort()
                }
            }
        }
    }

    fun release() {
        stopRecording()
        stopPlayback()
        scope.cancel()
    }
}

class BiquadFilter {
    private var b0 = 0f; private var b1 = 0f; private var b2 = 0f
    private var a1 = 0f; private var a2 = 0f
    private var x1 = 0f; private var x2 = 0f; private var y1 = 0f; private var y2 = 0f

    fun configurePeaking(f0: Float, gain: Float, q: Float, sampleRate: Float) {
        val A = Math.pow(10.0, gain / 40.0).toFloat()
        val w0 = (2.0 * Math.PI * f0 / sampleRate).toFloat()
        val sinW0 = Math.sin(w0.toDouble()).toFloat()
        val cosW0 = Math.cos(w0.toDouble()).toFloat()
        val alpha = sinW0 / (2f * q)
        val b0r = 1f + alpha * A
        val b1r = -2f * cosW0
        val b2r = 1f - alpha * A
        val a0r = 1f + alpha / A
        val a1r = -2f * cosW0
        val a2r = 1f - alpha / A
        b0 = b0r / a0r; b1 = b1r / a0r; b2 = b2r / a0r
        a1 = a1r / a0r; a2 = a2r / a0r
    }

    fun configureLowShelf(f0: Float, gain: Float, q: Float, sampleRate: Float) {
        val A = Math.pow(10.0, gain / 40.0).toFloat()
        val w0 = (2.0 * Math.PI * f0 / sampleRate).toFloat()
        val sinW0 = Math.sin(w0.toDouble()).toFloat()
        val cosW0 = Math.cos(w0.toDouble()).toFloat()
        val sqrtA2alpha = Math.sqrt(2.0 * A * calcAlpha(f0, q, sampleRate)).toFloat()
        val ap = A + 1f; val am = A - 1f
        val b0r = A * (ap - am * cosW0 + sqrtA2alpha)
        val b1r = 2f * A * (am - ap * cosW0)
        val b2r = A * (ap - am * cosW0 - sqrtA2alpha)
        val a0r = ap + am * cosW0 + sqrtA2alpha
        val a1r = -2f * (am + ap * cosW0)
        val a2r = ap + am * cosW0 - sqrtA2alpha
        b0 = b0r / a0r; b1 = b1r / a0r; b2 = b2r / a0r
        a1 = a1r / a0r; a2 = a2r / a0r
    }

    fun configureHighShelf(f0: Float, gain: Float, q: Float, sampleRate: Float) {
        val A = Math.pow(10.0, gain / 40.0).toFloat()
        val w0 = (2.0 * Math.PI * f0 / sampleRate).toFloat()
        val sinW0 = Math.sin(w0.toDouble()).toFloat()
        val cosW0 = Math.cos(w0.toDouble()).toFloat()
        val sqrtA2alpha = Math.sqrt(2.0 * A * calcAlpha(f0, q, sampleRate)).toFloat()
        val ap = A + 1f; val am = A - 1f
        val b0r = A * (ap + am * cosW0 + sqrtA2alpha)
        val b1r = -2f * A * (am + ap * cosW0)
        val b2r = A * (ap + am * cosW0 - sqrtA2alpha)
        val a0r = ap - am * cosW0 + sqrtA2alpha
        val a1r = 2f * (am - ap * cosW0)
        val a2r = ap - am * cosW0 - sqrtA2alpha
        b0 = b0r / a0r; b1 = b1r / a0r; b2 = b2r / a0r
        a1 = a1r / a0r; a2 = a2r / a0r
    }

    private fun calcAlpha(f0: Float, q: Float, sampleRate: Float): Float {
        val w0 = (2.0 * Math.PI * f0 / sampleRate).toFloat()
        return Math.sin(w0.toDouble()).toFloat() / (2f * q)
    }

    fun process(sample: Float): Float {
        val output = b0 * sample + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = sample; y2 = y1; y1 = output
        return output
    }

    fun reset() { x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f }
}
