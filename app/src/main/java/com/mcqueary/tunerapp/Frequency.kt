package com.mcqueary.tunerapp

class Frequency {
    var peak: Short = VOLUME_SENSITIVITY
    var peakMark = -1 // first iteration starts the chain frequency at Zero
    var rising: Boolean = false
    var sampleCount = 0
    var sampleStart = false

    fun frequency(): Double {
        val multiplyBy: Double = SAMPLE_RATE.toDouble() / sampleCount.toDouble()
        return peakMark.toDouble() * multiplyBy
    }
}