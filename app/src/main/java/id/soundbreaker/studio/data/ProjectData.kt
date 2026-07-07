package id.soundbreaker.studio.data

import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

data class ProjectData(
    val name: String = "My Song 01",
    val bpm: Int = 120,
    val timeSignatureNumerator: Int = 4,
    val timeSignatureDenominator: Int = 4,
    val isLooping: Boolean = true,
    val isClickOn: Boolean = false,
    val tracks: List<TrackData> = emptyList(),
)

data class TrackData(
    val id: Int,
    val name: String,
    val type: String,
    val colorHex: String,
    val volume: Float,
    val pan: Float,
    val sampleRate: Int,
    val audioFile: String?,
    val regions: List<RegionData>,
)

data class RegionData(
    val id: Int,
    val name: String,
    val startBar: Float,
    val widthBars: Float,
    val audioOffsetBars: Float = 0f,
)

fun ProjectState.toProjectData(): ProjectData {
    return ProjectData(
        name = name,
        bpm = bpm,
        timeSignatureNumerator = timeSignatureNumerator,
        timeSignatureDenominator = timeSignatureDenominator,
        isLooping = isLooping,
        isClickOn = isClickOn,
        tracks = tracks.map { track ->
            TrackData(
                id = track.id,
                name = track.name,
                type = track.type.name,
                colorHex = String.format("#%02X%02X%02X",
                    (track.color.red * 255).toInt(),
                    (track.color.green * 255).toInt(),
                    (track.color.blue * 255).toInt()),
                volume = track.volume,
                pan = track.pan,
                sampleRate = track.sampleRate,
                audioFile = null,
                regions = track.regions.map { region ->
                    RegionData(
                        id = region.id,
                        name = region.name,
                        startBar = region.startBar,
                        widthBars = region.widthBars,
                        audioOffsetBars = region.audioOffsetBars,
                    )
                }
            )
        }
    )
}

fun ProjectData.toJson(): String {
    val root = JSONObject()
    root.put("name", name)
    root.put("bpm", bpm)
    root.put("timeSignatureNumerator", timeSignatureNumerator)
    root.put("timeSignatureDenominator", timeSignatureDenominator)
    root.put("isLooping", isLooping)
    root.put("isClickOn", isClickOn)

    val tracksArray = JSONArray()
    for (track in tracks) {
        val trackObj = JSONObject()
        trackObj.put("id", track.id)
        trackObj.put("name", track.name)
        trackObj.put("type", track.type)
        trackObj.put("color", track.colorHex)
        trackObj.put("volume", track.volume.toDouble())
        trackObj.put("pan", track.pan.toDouble())
        trackObj.put("sampleRate", track.sampleRate)
        trackObj.put("audioFile", track.audioFile ?: "")

        val regionsArray = JSONArray()
        for (region in track.regions) {
            val regionObj = JSONObject()
            regionObj.put("id", region.id)
            regionObj.put("name", region.name)
            regionObj.put("startBar", region.startBar.toDouble())
            regionObj.put("widthBars", region.widthBars.toDouble())
            regionObj.put("audioOffsetBars", region.audioOffsetBars.toDouble())
            regionsArray.put(regionObj)
        }
        trackObj.put("regions", regionsArray)
        tracksArray.put(trackObj)
    }
    root.put("tracks", tracksArray)

    return root.toString(2)
}

fun parseProjectData(json: String): ProjectData? {
    try {
        val root = JSONObject(json)
        val name = root.getString("name")
        val bpm = root.getInt("bpm")
        val tsNum = root.getInt("timeSignatureNumerator")
        val tsDen = root.getInt("timeSignatureDenominator")
        val isLooping = root.optBoolean("isLooping", true)
        val isClickOn = root.optBoolean("isClickOn", false)

        val tracksArray = root.getJSONArray("tracks")
        val tracks = mutableListOf<TrackData>()

        for (i in 0 until tracksArray.length()) {
            val trackObj = tracksArray.getJSONObject(i)
            val regionsArray = trackObj.getJSONArray("regions")
            val regions = mutableListOf<RegionData>()

            for (j in 0 until regionsArray.length()) {
                val regionObj = regionsArray.getJSONObject(j)
                regions.add(RegionData(
                    id = regionObj.getInt("id"),
                    name = regionObj.getString("name"),
                    startBar = regionObj.getDouble("startBar").toFloat(),
                    widthBars = regionObj.getDouble("widthBars").toFloat(),
                    audioOffsetBars = regionObj.optDouble("audioOffsetBars", 0.0).toFloat(),
                ))
            }

            tracks.add(TrackData(
                id = trackObj.getInt("id"),
                name = trackObj.getString("name"),
                type = trackObj.getString("type"),
                colorHex = run {
                    val colorValue = trackObj.get("color")
                    when (colorValue) {
                        is String -> colorValue
                        is Number -> {
                            // Legacy Long format - convert to hex
                            val v = colorValue.toLong()
                            // Compose Color ULong: extract RGB from lower bits
                            val r = (v shr 16) and 0xFF
                            val g = (v shr 8) and 0xFF
                            val b = v and 0xFF
                            String.format("#%02X%02X%02X", r, g, b)
                        }
                        else -> "#FF4757"
                    }
                },
                volume = trackObj.getDouble("volume").toFloat(),
                pan = trackObj.getDouble("pan").toFloat(),
                sampleRate = trackObj.optInt("sampleRate", 44100),
                audioFile = trackObj.optString("audioFile", ""),
                regions = regions,
            ))
        }

        return ProjectData(
            name = root.optString("name", "Untitled"),
            bpm = root.optInt("bpm", 120),
            timeSignatureNumerator = root.optInt("timeSignatureNumerator", 4),
            timeSignatureDenominator = root.optInt("timeSignatureDenominator", 4),
            isLooping = root.optBoolean("isLooping", true),
            isClickOn = root.optBoolean("isClickOn", false),
            tracks = tracks,
        )
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

fun ProjectData.toProjectState(): ProjectState {
    return ProjectState(
        name = name,
        bpm = bpm,
        timeSignatureNumerator = timeSignatureNumerator,
        timeSignatureDenominator = timeSignatureDenominator,
        isLooping = isLooping,
        isClickOn = isClickOn,
        tracks = tracks.map { track ->
            Track(
                id = track.id,
                name = track.name,
                type = try { TrackType.valueOf(track.type) } catch (e: Exception) { TrackType.AUDIO_STEREO },
                color = try { Color(android.graphics.Color.parseColor(track.colorHex)) } catch (e: Exception) { Color(0xFFFF4757) },
                volume = track.volume,
                pan = track.pan,
                sampleRate = track.sampleRate,
                regions = track.regions.map { region ->
                    AudioRegion(
                        id = region.id,
                        name = region.name,
                        startBar = region.startBar,
                        widthBars = region.widthBars,
                        waveform = null,
                        audioOffsetBars = region.audioOffsetBars,
                    )
                },
            )
        },
    )
}
