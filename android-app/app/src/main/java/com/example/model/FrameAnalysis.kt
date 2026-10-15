package com.example.model

import android.graphics.Bitmap

/**
 * Encapsulates a raw captured frame and its decoded Bitmap.
 */
data class CapturedFrame(
    val index: Int,
    val jpegBytes: ByteArray,
    val bitmap: Bitmap
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CapturedFrame
        return index == other.index && jpegBytes.contentEquals(other.jpegBytes)
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + jpegBytes.contentHashCode()
        return result
    }
}

/**
 * Selected frame with its quality score.
 */
data class BestFrameSelection(
    val frame: CapturedFrame,
    val score: FrameScoreResult,
    val allScores: List<FrameScoreResult>
)
