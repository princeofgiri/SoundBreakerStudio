package id.soundbreaker.studio.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
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

    private var playbackBuffers: List<ShortArray> = emptyList()
    private var playbackPosition = 0

    var onAmplitude: ((Float) -> Unit)? = null
    var onRecordComplete: ((File) -> Unit)? = null
    var onPlaybackPosition: ((Int, Int) -> Unit)? = null
    var onPlaybackFinished: (() -> Unit)? = null

    fun startRecording(outputFile: File) {
        if (isRecording) return

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT)
            .coerceAtLeast(SAMPLE_RATE * BYTES_PER_SAMPLE * CHANNELS)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG_IN,
            AUDIO_FORMAT,
            bufferSize
        )

        recordedBuffers.clear()
        totalRecordedFrames = 0
        isRecording = true

        audioRecord?.startRecording()

        recordJob = scope.launch {
            val buffer = ShortArray(bufferSize / BYTES_PER_SAMPLE)

            while (isActive && isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read > 0) {
                    val copy = buffer.copyOf(read)
                    recordedBuffers.add(copy)
                    totalRecordedFrames += read / CHANNELS

                    val maxAmplitude = copy.maxOfOrNull { Math.abs(it.toInt()) } ?: 0
                    onAmplitude?.invoke(maxAmplitude / Short.MAX_VALUE.toFloat())
                }
            }

            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            isRecording = false

            val pcmData = mergeBuffers()
            writeWavFile(pcmData, outputFile)
            onRecordComplete?.invoke(outputFile)
        }
    }

    fun stopRecording() {
        isRecording = false
        recordJob?.cancel()
    }

    fun startPlaybackFromPosition(trackPcmData: List<ShortArray>, startFrame: Int) {
        if (isPlaying) stopPlayback()

        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT
        )
        val bufferSize = (minBuffer * 2).coerceAtLeast(8192)

        audioTrack = AudioTrack.Builder()
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

        playbackBuffers = trackPcmData
        playbackPosition = startFrame.coerceAtLeast(0)
        isPlaying = true

        audioTrack?.play()

        playJob = scope.launch {
            val framesPerChunk = 2048
            val totalFrames = trackPcmData.maxOfOrNull { it.size / 2 } ?: 0

            while (isActive && isPlaying && playbackPosition < totalFrames) {
                if (isPaused) {
                    kotlinx.coroutines.delay(50)
                    continue
                }
                val framesToRead = minOf(framesPerChunk, totalFrames - playbackPosition)
                val stereoOutput = mixMultipleTracks(playbackBuffers, playbackPosition, framesToRead)

                val written = audioTrack?.write(stereoOutput, 0, stereoOutput.size) ?: -1
                if (written > 0) {
                    playbackPosition += framesToRead
                    onPlaybackPosition?.invoke(playbackPosition, totalFrames)
                } else {
                    break
                }
            }

            audioTrack?.stop()
            audioTrack?.release()
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

    fun stopPlayback() {
        isPlaying = false
        playJob?.cancel()
        audioTrack?.stop()
        audioTrack?.release()
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

    fun isRecording() = isRecording
    fun isPlaying() = isPlaying

    private fun mixMultipleTracks(
        tracks: List<ShortArray>,
        startFrame: Int,
        maxFrames: Int
    ): ShortArray {
        // tracks contain stereo interleaved data: L0 R0 L1 R1...
        // Output is also stereo interleaved
        val stereoOutput = ShortArray(maxFrames * 2)

        for (i in 0 until maxFrames) {
            var leftSum = 0L
            var rightSum = 0L
            for (track in tracks) {
                val sampleIdx = (startFrame + i) * 2
                if (sampleIdx + 1 < track.size) {
                    leftSum += track[sampleIdx].toLong()
                    rightSum += track[sampleIdx + 1].toLong()
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

    fun readWavFile(file: File): ShortArray? {
        try {
            val bytes = file.readBytes()
            if (bytes.size < 44) return null

            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

            // Read channels from header (offset 22)
            val channels = buffer.getShort(22).toInt().coerceIn(1, 2)

            // Skip to data (offset 44)
            buffer.position(44)

            val dataSize = bytes.size - 44
            val bitsPerSample = 16
            val frameSize = channels * (bitsPerSample / 8)
            val totalFrames = dataSize / frameSize

            val pcmData = ShortArray(totalFrames * channels)
            for (i in pcmData.indices) {
                if (buffer.hasRemaining()) {
                    pcmData[i] = buffer.short
                }
            }
            android.util.Log.i("SoundBreaker", "readWav OK: ch=$channels, frames=$totalFrames, size=${pcmData.size}")
            return pcmData
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
            val headerBuf = ByteArray(44)
            inputStream.read(headerBuf)
            val header = ByteBuffer.wrap(headerBuf).order(ByteOrder.LITTLE_ENDIAN)

            val channels = header.getShort(22).toInt().coerceIn(1, 2)
            val fileSampleRate = header.getInt(24)
            val dataSize = headerBuf.size - 44
            val frameSize = channels * BYTES_PER_SAMPLE

            android.util.Log.e("SoundBreaker", "WAV header: sr=$fileSampleRate, ch=$channels")

            val chunkSize = 65536
            val buffers = mutableListOf<ShortArray>()
            var totalRead = 0

            while (totalRead < dataSize) {
                val toRead = minOf(chunkSize, dataSize - totalRead)
                val chunkBuf = ByteArray(toRead)
                val read = inputStream.read(chunkBuf)
                if (read <= 0) break

                val shorts = ShortArray(read / BYTES_PER_SAMPLE)
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
            for (buf in buffers) {
                System.arraycopy(buf, 0, pcmData, offset, buf.size)
                offset += buf.size
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

    fun release() {
        stopRecording()
        stopPlayback()
        scope.cancel()
    }
}
