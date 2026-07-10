package id.soundbreaker.studio.audio

import id.soundbreaker.studio.data.Effect
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioExporter(
    private val sampleRate: Int = AudioEngine.SAMPLE_RATE,
    private val channels: Int = 2,
) {
    private val masterEqProcessor = MasterEqProcessor(sampleRate)
    private val effectsChain = TrackEffectsChain(sampleRate)

    data class ExportConfig(
        val trackPcmData: Map<Int, ShortArray>,
        val trackIds: List<Int>,
        val volumes: List<Float>,
        val pans: List<Float>,
        val eq: List<Triple<Float, Float, Float>>,
        val effects: List<List<Effect>>,
        val bpm: Int,
        val isClickOn: Boolean,
        val masterEq: List<Float>,
        val masterEqEnabled: Boolean,
        val masterVolume: Float,
        val masterPan: Float,
    )

    fun exportToWav(config: ExportConfig, outputFile: File, onProgress: ((Float) -> Unit)? = null): Boolean {
        try {
            val totalFrames = config.trackIds.maxOfOrNull { id ->
                val pcm = config.trackPcmData[id] ?: ShortArray(0)
                pcm.size / channels
            } ?: 0
            if (totalFrames == 0) return false

            val sr = sampleRate.toFloat()
            val eqFilters = Array(config.trackIds.size) { idx ->
                val (low, mid, high) = config.eq.getOrElse(idx) { Triple(0f, 0f, 0f) }
                arrayOf(
                    BiquadFilter().apply { configureLowShelf(200f, low, 0.707f, sr) },
                    BiquadFilter().apply { configurePeaking(1000f, mid, 1.0f, sr) },
                    BiquadFilter().apply { configureHighShelf(4000f, high, 0.707f, sr) },
                )
            }

            if (config.masterEqEnabled && config.masterEq.isNotEmpty()) {
                masterEqProcessor.setGains(config.masterEq)
            }

            val frameSize = channels * 2
            val totalDataBytes = totalFrames * frameSize
            val raf = RandomAccessFile(outputFile, "rw")
            writeWavHeader(raf, totalDataBytes)

            var framesWritten = 0
            val framesPerChunk = 4096

            while (framesWritten < totalFrames) {
                val framesToMix = minOf(framesPerChunk, totalFrames - framesWritten)
                val stereoOutput = ShortArray(framesToMix * 2)

                for (i in 0 until framesToMix) {
                    var leftSum = 0L
                    var rightSum = 0L
                    for ((trackIdx, trackId) in config.trackIds.withIndex()) {
                        val pcm = config.trackPcmData[trackId] ?: continue
                        val vol = config.volumes.getOrElse(trackIdx) { 1f }
                        if (vol <= 0.001f) continue
                        val pan = config.pans.getOrElse(trackIdx) { 0.5f }
                        val panAngle = pan * Math.PI.toFloat() / 2f
                        val leftGain = Math.cos(panAngle.toDouble()).toFloat()
                        val rightGain = Math.sin(panAngle.toDouble()).toFloat()

                        val sampleIdx = (framesWritten + i) * 2
                        if (sampleIdx + 1 < pcm.size) {
                            var left = pcm[sampleIdx].toFloat() * vol
                            var right = pcm[sampleIdx + 1].toFloat() * vol

                            if (trackIdx < eqFilters.size) {
                                val filters = eqFilters[trackIdx]
                                left = filters[0].process(left)
                                left = filters[1].process(left)
                                left = filters[2].process(left)
                                right = filters[0].process(right)
                                right = filters[1].process(right)
                                right = filters[2].process(right)
                            }

                            val trackFx = config.effects.getOrElse(trackIdx) { emptyList() }
                            if (trackFx.isNotEmpty()) {
                                left = effectsChain.processTrack(trackId, left, trackFx)
                                right = effectsChain.processTrack(trackId + 10000, right, trackFx)
                            }

                            leftSum += (left * leftGain).toLong()
                            rightSum += (right * rightGain).toLong()
                        }
                    }
                    stereoOutput[i * 2] = leftSum.coerceIn(Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong()).toShort()
                    stereoOutput[i * 2 + 1] = rightSum.coerceIn(Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong()).toShort()
                }

                if (config.isClickOn && config.bpm > 0) {
                    mixClickTrack(stereoOutput, framesToMix, framesWritten, config.bpm)
                }

                if (config.masterEqEnabled) {
                    masterEqProcessor.processStereo(stereoOutput, framesToMix)
                }

                val mv = config.masterVolume
                val mp = config.masterPan
                val mpLeft = Math.cos(mp.toDouble() * Math.PI / 2.0).toFloat()
                val mpRight = Math.sin(mp.toDouble() * Math.PI / 2.0).toFloat()
                for (j in stereoOutput.indices step 2) {
                    stereoOutput[j] = (stereoOutput[j].toFloat() * mv * mpLeft).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    stereoOutput[j + 1] = (stereoOutput[j + 1].toFloat() * mv * mpRight).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }

                val buf = ByteBuffer.allocate(stereoOutput.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                for (s in stereoOutput) buf.putShort(s)
                buf.flip()
                val bytes = ByteArray(buf.remaining())
                buf.get(bytes)
                raf.write(bytes)

                framesWritten += framesToMix
                onProgress?.invoke(framesWritten.toFloat() / totalFrames)
            }

            val actualDataSize = raf.length() - 44
            raf.seek(4)
            raf.write(intToBytesLE((36 + actualDataSize).toInt()))
            raf.seek(40)
            raf.write(intToBytesLE(actualDataSize.toInt()))
            raf.close()

            return true
        } catch (e: Exception) {
            android.util.Log.e("AudioExporter", "Export failed: ${e.message}", e)
            return false
        }
    }

    private fun mixClickTrack(output: ShortArray, frames: Int, startFrame: Int, bpm: Int) {
        val framesPerBeat = (sampleRate.toLong() * 60 / bpm).toInt()
        val clickDuration = (sampleRate * 0.005).toInt().coerceAtLeast(1)
        for (i in 0 until frames) {
            val framePos = startFrame + i
            val posInBeat = framePos % framesPerBeat
            if (posInBeat < clickDuration) {
                val freq = if (framePos / framesPerBeat % 4 == 0) 1000f else 800f
                val progress = posInBeat.toFloat() / clickDuration
                val envelope = (1f - progress) * 0.5f
                val sample = (Math.sin(2.0 * Math.PI * freq * posInBeat / sampleRate) * Short.MAX_VALUE * envelope).toInt().toShort()
                val idx = i * 2
                if (idx + 1 < output.size) {
                    output[idx] = (output[idx].toLong() + sample.toLong()).coerceIn(Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong()).toShort()
                    output[idx + 1] = (output[idx + 1].toLong() + sample.toLong()).coerceIn(Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong()).toShort()
                }
            }
        }
    }

    private fun writeWavHeader(raf: RandomAccessFile, dataSize: Int) {
        val totalSize = 36 + dataSize
        val frameSize = channels * 2
        val byteRate = sampleRate * frameSize

        raf.seek(0)
        raf.write("RIFF".toByteArray())
        raf.write(intToBytesLE(totalSize))
        raf.write("WAVE".toByteArray())
        raf.write("fmt ".toByteArray())
        raf.write(intToBytesLE(16))
        raf.write(shortToBytesLE(1))
        raf.write(shortToBytesLE(channels.toShort()))
        raf.write(intToBytesLE(sampleRate))
        raf.write(intToBytesLE(byteRate))
        raf.write(shortToBytesLE(frameSize.toShort()))
        raf.write(shortToBytesLE(16))
        raf.write("data".toByteArray())
        raf.write(intToBytesLE(dataSize))
    }

    private fun intToBytesLE(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        (value shr 8 and 0xFF).toByte(),
        (value shr 16 and 0xFF).toByte(),
        (value shr 24 and 0xFF).toByte(),
    )

    private fun shortToBytesLE(value: Short): ByteArray = byteArrayOf(
        (value.toInt() and 0xFF).toByte(),
        (value.toInt() shr 8 and 0xFF).toByte(),
    )
}
