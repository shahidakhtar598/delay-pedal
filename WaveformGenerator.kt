package com.naatdelay.dd7.audio

import kotlin.math.abs
import kotlin.math.max

object WaveformGenerator {

    const val DEFAULT_BAR_COUNT = 200

    fun toJson(peaks: List<Float>): String =
        peaks.joinToString(prefix = "[", postfix = "]", separator = ",") {
            "%.3f".format(it.coerceIn(0f, 1f))
        }

    fun fromJson(json: String?): List<Float> {
        if (json.isNullOrBlank()) return emptyList()
        return json.trim()
            .removePrefix("[")
            .removeSuffix("]")
            .split(",")
            .mapNotNull { it.trim().toFloatOrNull()?.coerceIn(0f, 1f) }
    }

    fun downsamplePeaks(rawBlockPeaks: List<Float>, targetBars: Int = DEFAULT_BAR_COUNT): List<Float> {
        if (rawBlockPeaks.isEmpty()) return List(targetBars) { 0.08f }
        val maxVal = rawBlockPeaks.maxOrNull()?.takeIf { it > 0.001f } ?: 1f
        val result = MutableList(targetBars) { 0f }
        val step = rawBlockPeaks.size.toFloat() / targetBars.toFloat()

        for (i in 0 until targetBars) {
            val start = (i * step).toInt().coerceIn(0, rawBlockPeaks.lastIndex)
            val end = ((i + 1) * step).toInt().coerceIn(start + 1, rawBlockPeaks.size)
            var peak = 0f
            for (j in start until end) {
                peak = max(peak, abs(rawBlockPeaks[j]))
            }
            result[i] = (peak / maxVal).coerceIn(0.05f, 1f)
        }
        return result
    }
}
