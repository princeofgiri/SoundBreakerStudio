package id.soundbreaker.studio.audio

import kotlin.math.*

class CompressorProcessor {
    var threshold = 0.5f
    var ratio = 4f
    private var envelope = 0f

    fun process(sample: Float): Float {
        val thresholdAmplitude = threshold * Short.MAX_VALUE
        val absSample = abs(sample)
        val gainReduction = if (absSample > thresholdAmplitude) {
            val overDb = 20f * log10(absSample / thresholdAmplitude).coerceAtLeast(0.001f)
            val compressedDb = overDb * (1f - 1f / ratio)
            10f.pow(-compressedDb / 20f)
        } else 1f
        envelope = envelope * 0.95f + gainReduction * 0.05f
        return sample * envelope
    }
}

class ReverbProcessor(private val sampleRate: Int = 44100) {
    var amount = 0.3f
    var decay = 0.5f
    private val delayLine = FloatArray(sampleRate / 2)
    private var writePos = 0
    private var readPos1 = 0
    private var readPos2 = 0

    init {
        readPos1 = (sampleRate * 0.037).toInt()
        readPos2 = (sampleRate * 0.059).toInt()
    }

    fun process(sample: Float): Float {
        val delayed = delayLine[readPos1]
        val out = sample * (1f - amount) + delayed * amount
        delayLine[writePos] = sample + delayed * decay
        writePos = (writePos + 1) % delayLine.size
        readPos1 = (readPos1 + 1) % delayLine.size
        readPos2 = (readPos2 + 1) % delayLine.size
        return out
    }
}

class DelayProcessor(private val sampleRate: Int = 44100) {
    var time = 0.3f
    var feedback = 0.4f
    private val delayLine = FloatArray(sampleRate * 2)
    private var writePos = 0

    fun process(sample: Float): Float {
        val delaySamples = (time * sampleRate).toInt().coerceIn(0, delayLine.size - 1)
        val readPos = (writePos - delaySamples + delayLine.size) % delayLine.size
        val delayed = delayLine[readPos]
        delayLine[writePos] = sample + delayed * feedback
        writePos = (writePos + 1) % delayLine.size
        return sample + delayed * amount(time)
    }

    private fun amount(t: Float) = t * 0.6f
}

class ChorusProcessor(private val sampleRate: Int = 44100) {
    var depth = 0.3f
    var rate = 0.5f
    private val delayLine = FloatArray(sampleRate / 5)
    private var writePos = 0
    private var phase = 0f

    fun process(sample: Float): Float {
        delayLine[writePos] = sample
        val modDelay = (depth * 0.003f * sampleRate * (0.5f + 0.5f * sin(2.0 * PI * rate * phase / sampleRate))).toInt()
            .coerceIn(0, delayLine.size - 1)
        phase++
        val readPos = (writePos - modDelay + delayLine.size) % delayLine.size
        val delayed = delayLine[readPos]
        writePos = (writePos + 1) % delayLine.size
        return sample + delayed * 0.5f
    }
}

class DistortionProcessor {
    var drive = 0.3f
    var tone = 0.5f
    private var lastOut = 0f

    fun process(sample: Float): Float {
        val gain = 1f + drive * 19f
        val driven = tanh(sample * gain)
        lastOut = lastOut * (1f - tone) + driven * tone
        return lastOut
    }
}

class FilterProcessor(private val sampleRate: Int = 44100) {
    var cutoff = 0.8f
    var resonance = 0.3f
    private var prevInput = 0f
    private var prevOutput = 0f

    fun process(sample: Float): Float {
        val freq = cutoff * 0.9f * sampleRate / 2f
        val rc = 1f / (2f * PI.toFloat() * freq)
        val dt = 1f / sampleRate
        val alpha = dt / (rc + dt)
        val output = prevOutput + alpha * (sample - prevOutput)
        val reso = output + (output - prevInput) * resonance * 0.3f
        prevInput = sample
        prevOutput = output
        return reso.coerceIn(-1f, 1f)
    }
}

class TrackEffectsChain(private val sampleRate: Int = 44100) {
    private val compressors = mutableMapOf<Int, CompressorProcessor>()
    private val reverbs = mutableMapOf<Int, ReverbProcessor>()
    private val delays = mutableMapOf<Int, DelayProcessor>()
    private val choruses = mutableMapOf<Int, ChorusProcessor>()
    private val distortions = mutableMapOf<Int, DistortionProcessor>()
    private val filters = mutableMapOf<Int, FilterProcessor>()

    fun processTrack(trackId: Int, sample: Float, effects: List<id.soundbreaker.studio.data.Effect>): Float {
        var out = sample
        for (fx in effects) {
            if (!fx.isEnabled) continue
            when {
                fx.name == "Compressor" -> {
                    val p = compressors.getOrPut(trackId) { CompressorProcessor() }
                    p.threshold = fx.params["threshold"] ?: 0.5f
                    p.ratio = fx.params["ratio"] ?: 4f
                    out = p.process(out)
                }
                fx.name == "Reverb" -> {
                    val p = reverbs.getOrPut(trackId) { ReverbProcessor(sampleRate) }
                    p.amount = fx.params["amount"] ?: 0.3f
                    p.decay = fx.params["decay"] ?: 0.5f
                    out = p.process(out)
                }
                fx.name == "Delay" -> {
                    val p = delays.getOrPut(trackId) { DelayProcessor(sampleRate) }
                    p.time = fx.params["time"] ?: 0.3f
                    p.feedback = fx.params["feedback"] ?: 0.4f
                    out = p.process(out)
                }
                fx.name == "Chorus" -> {
                    val p = choruses.getOrPut(trackId) { ChorusProcessor(sampleRate) }
                    p.depth = fx.params["depth"] ?: 0.3f
                    p.rate = fx.params["rate"] ?: 0.5f
                    out = p.process(out)
                }
                fx.name == "Distortion" -> {
                    val p = distortions.getOrPut(trackId) { DistortionProcessor() }
                    p.drive = fx.params["drive"] ?: 0.3f
                    p.tone = fx.params["tone"] ?: 0.5f
                    out = p.process(out)
                }
                fx.name == "Filter" -> {
                    val p = filters.getOrPut(trackId) { FilterProcessor(sampleRate) }
                    p.cutoff = fx.params["cutoff"] ?: 0.8f
                    p.resonance = fx.params["resonance"] ?: 0.3f
                    out = p.process(out)
                }
            }
        }
        return out
    }

    fun reset(trackId: Int) {
        compressors.remove(trackId)
        reverbs.remove(trackId)
        delays.remove(trackId)
        choruses.remove(trackId)
        distortions.remove(trackId)
        filters.remove(trackId)
    }
}
