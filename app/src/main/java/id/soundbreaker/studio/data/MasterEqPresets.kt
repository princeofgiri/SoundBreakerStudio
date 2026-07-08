package id.soundbreaker.studio.data

object MasterEqPresets {
    val bandFrequencies = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    val bandLabels = listOf("31", "62", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")

    val presets = mapOf(
        "Flat" to List(10) { 0f },
        "Rock" to listOf(4f, 3f, 1f, -1f, -2f, 1f, 3f, 4f, 3f, 1f),
        "Pop" to listOf(-2f, -1f, 0f, 2f, 4f, 4f, 2f, 0f, -1f, -2f),
        "Jazz" to listOf(3f, 2f, 0f, 1f, -1f, -1f, 0f, 2f, 3f, 2f),
        "Classical" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 2f, 3f, 4f, 3f),
        "Electronic" to listOf(5f, 3f, 0f, -2f, 1f, -1f, 1f, 3f, 4f, 5f),
        "Hip-Hop" to listOf(6f, 4f, 1f, -1f, 0f, 2f, 3f, 1f, -1f, -2f),
        "Acoustic" to listOf(2f, 1f, 0f, 1f, 2f, 2f, 1f, 2f, 3f, 1f),
        "Bright" to listOf(-3f, -2f, -1f, 0f, 1f, 3f, 5f, 6f, 5f, 4f),
        "Warm" to listOf(5f, 3f, 1f, 0f, -1f, -2f, -1f, 0f, 1f, 0f),
        "Vocal" to listOf(-3f, -1f, 1f, 3f, 4f, 4f, 3f, 1f, 0f, -1f),
        "Bass Boost" to listOf(8f, 6f, 3f, 0f, -1f, -1f, 0f, 0f, 0f, 0f),
        "Bass" to listOf(8f, 6f, 3f, 0f, -1f, -1f, 0f, 0f, 0f, 0f),
        "V-Shape" to listOf(4f, 2.5f, 1f, -1.5f, -3f, -3f, -1.5f, 1f, 2.5f, 4f),
        "Country" to listOf(1f, 1.5f, 2f, 1f, 0f, 0.5f, 1.5f, 2f, 1f, 0.5f),
        "Punk" to listOf(-1f, 0f, 1.5f, 3f, 4f, 3.5f, 2f, 1.5f, 0.5f, -1f),
    )
}
