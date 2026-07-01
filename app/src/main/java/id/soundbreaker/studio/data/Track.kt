package id.soundbreaker.studio.data

import androidx.compose.ui.graphics.Color

data class Track(
    val id: Int,
    val name: String,
    val type: TrackType,
    val color: Color,
    val volume: Float = 0.75f,
    val pan: Float = 0.5f,
    val isMuted: Boolean = false,
    val isSolo: Boolean = false,
    val isArmed: Boolean = false,
    val sampleRate: Int = 44100,
    val regions: List<AudioRegion> = emptyList(),
    val effects: List<Effect> = emptyList(),
)

enum class TrackType(val label: String) {
    AUDIO_MONO("Audio · Mono"),
    AUDIO_STEREO("Audio · Stereo"),
    MIDI_INSTRUMENT("MIDI · Instrument"),
    MIDI_DRUM("MIDI · Drum Kit"),
}

data class AudioRegion(
    val id: Int,
    val name: String,
    val startBar: Float,
    val widthBars: Float,
    val waveform: FloatArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioRegion) return false
        return id == other.id
    }
    override fun hashCode(): Int = id
}

data class Effect(
    val id: Int,
    val name: String,
    val icon: String,
    val isEnabled: Boolean = true,
)

data class ProjectState(
    val name: String = "My Song 01",
    val bpm: Int = 120,
    val timeSignatureNumerator: Int = 4,
    val timeSignatureDenominator: Int = 4,
    val isPlaying: Boolean = false,
    val isRecording: Boolean = false,
    val isLooping: Boolean = true,
    val isClickOn: Boolean = false,
    val currentBar: Int = 1,
    val currentBeat: Int = 1,
    val currentTick: Int = 0,
    val playheadPosition: Float = 1f,
    val totalBars: Int = 200,
    val tracks: List<Track> = emptyList(),
)

fun defaultTracks(): List<Track> = listOf(
    Track(
        id = 1,
        name = "Lead Vocal",
        type = TrackType.AUDIO_MONO,
        color = Color(0xFFFF4757),
        volume = 0.80f,
        regions = listOf(
            AudioRegion(1, "Verse 1.wav", 1f, 4f),
            AudioRegion(2, "Chorus.wav", 5.5f, 3.5f),
            AudioRegion(3, "Verse 2.wav", 10f, 3f),
        ),
        effects = listOf(
            Effect(1, "Compressor", "🎛", true),
            Effect(2, "Reverb", "🌊", true),
            Effect(4, "De-Esser", "🎤", true),
        ),
    ),
    Track(
        id = 2,
        name = "Acoustic Guitar",
        type = TrackType.AUDIO_STEREO,
        color = Color(0xFF3498DB),
        volume = 0.65f,
        regions = listOf(
            AudioRegion(4, "Guitar Full Take.wav", 0.5f, 6f),
            AudioRegion(5, "Guitar Overdub.wav", 7f, 5f),
        ),
    ),
    Track(
        id = 3,
        name = "Bass",
        type = TrackType.MIDI_INSTRUMENT,
        color = Color(0xFF2ED573),
        volume = 0.72f,
        regions = listOf(
            AudioRegion(6, "Bass MIDI Clip", 0.5f, 12f),
        ),
    ),
    Track(
        id = 4,
        name = "Drums",
        type = TrackType.MIDI_DRUM,
        color = Color(0xFFFFA502),
        volume = 0.85f,
        regions = listOf(
            AudioRegion(7, "Kick Pattern", 0.5f, 5.5f),
            AudioRegion(8, "Full Kit", 6.5f, 6f),
        ),
    ),
    Track(
        id = 5,
        name = "Pad Synth",
        type = TrackType.MIDI_INSTRUMENT,
        color = Color(0xFFA29BFE),
        volume = 0.55f,
        regions = listOf(
            AudioRegion(9, "Ambient Pad", 1f, 11.5f),
        ),
    ),
    Track(
        id = 6,
        name = "Background Vocal",
        type = TrackType.AUDIO_STEREO,
        color = Color(0xFFFD79A8),
        volume = 0.60f,
        regions = listOf(
            AudioRegion(10, "BG Vox Chorus.wav", 5.5f, 3.5f),
            AudioRegion(11, "BG Vox V2.wav", 10f, 3f),
        ),
    ),
    Track(
        id = 7,
        name = "Piano",
        type = TrackType.MIDI_INSTRUMENT,
        color = Color(0xFF00CEC9),
        volume = 0.48f,
        regions = listOf(
            AudioRegion(12, "Piano Chords MIDI", 1.5f, 11f),
        ),
    ),
)
