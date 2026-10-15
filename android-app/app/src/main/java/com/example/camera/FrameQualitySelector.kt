package com.example.camera

import android.graphics.Bitmap
import android.graphics.Color
import com.example.model.BestFrameSelection
import com.example.model.CapturedFrame
import com.example.model.FrameScoreResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max

/**
 * Scores and selects the highest quality frame among captured frames.
 *
 * Implements the SeeAssist Python prototype algorithm in native Kotlin:
 * 1. Grayscale luminance extraction.
 * 2. Sharpness score via discrete 2D Laplacian variance.
 * 3. Mean brightness calculation.
 * 4. Exposure score: max(0, 1 - abs(brightness - 128) / 128).
 * 5. Final quality score: sharpness * (0.5 + 0.5 * exposureScore).
 * 6. Select frame with the highest finalScore.
 */
class FrameQualitySelector {

    /**
     * Selects the best frame from a list of captured frames.
     */
    suspend fun selectBestFrame(frames: List<CapturedFrame>): Result<BestFrameSelection> = withContext(Dispatchers.Default) {
        if (frames.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Frame list cannot be empty"))
        }

        val scoredResults = mutableListOf<Pair<CapturedFrame, FrameScoreResult>>()

        for (frame in frames) {
            val scoreResult = evaluateFrameQuality(frame.bitmap, frame.index)
            scoredResults.add(Pair(frame, scoreResult))
        }

        // Pick the frame with highest finalScore
        val best = scoredResults.maxByOrNull { it.second.finalScore }
            ?: return@withContext Result.failure(IllegalStateException("Failed to calculate frame scores"))

        val allScores = scoredResults.map { it.second }

        Result.success(
            BestFrameSelection(
                frame = best.first,
                score = best.second,
                allScores = allScores
            )
        )
    }

    /**
     * Evaluates quality metrics for a single Bitmap.
     */
    fun evaluateFrameQuality(bitmap: Bitmap, frameIndex: Int): FrameScoreResult {
        // Normalize size for fast processing while maintaining detail
        val targetWidth = 320
        val targetHeight = if (bitmap.width > 0) {
            (bitmap.height * targetWidth) / bitmap.width
        } else 240

        val scaled = if (bitmap.width > targetWidth) {
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        } else {
            bitmap
        }

        val width = scaled.width
        val height = scaled.height
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)

        // 1. Grayscale luminance extraction & mean brightness
        val gray = FloatArray(width * height)
        var brightnessSum = 0.0

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val luminance = 0.299f * r + 0.587f * g + 0.114f * b
            gray[i] = luminance
            brightnessSum += luminance
        }

        val totalPixels = width * height
        val meanBrightness = (brightnessSum / totalPixels).toFloat()

        // 2. Discrete Laplacian variance (sharpness)
        // Kernel:
        //  [  0,  1,  0 ]
        //  [  1, -4,  1 ]
        //  [  0,  1,  0 ]
        var laplacianSum = 0.0
        val laplacianValues = FloatArray((width - 2) * (height - 2))
        var count = 0

        for (y in 1 until height - 1) {
            val yOffset = y * width
            val yPrevOffset = (y - 1) * width
            val yNextOffset = (y + 1) * width

            for (x in 1 until width - 1) {
                val center = gray[yOffset + x]
                val top = gray[yPrevOffset + x]
                val bottom = gray[yNextOffset + x]
                val left = gray[yOffset + (x - 1)]
                val right = gray[yOffset + (x + 1)]

                val lapVal = top + bottom + left + right - (4f * center)
                laplacianValues[count++] = lapVal
                laplacianSum += lapVal
            }
        }

        val meanLaplacian = if (count > 0) (laplacianSum / count) else 0.0
        var varianceSum = 0.0
        for (i in 0 until count) {
            val diff = laplacianValues[i] - meanLaplacian
            varianceSum += diff * diff
        }

        val sharpness = if (count > 0) (varianceSum / count).toFloat() else 0f

        // 4. Exposure score: max(0, 1 - abs(brightness - 128) / 128)
        val exposureScore = max(0f, 1f - abs(meanBrightness - 128f) / 128f)

        // 5. Final quality score: sharpness * (0.5 + 0.5 * exposureScore)
        val finalScore = sharpness * (0.5f + 0.5f * exposureScore)

        return FrameScoreResult(
            frameIndex = frameIndex,
            sharpness = sharpness,
            brightness = meanBrightness,
            exposureScore = exposureScore,
            finalScore = finalScore
        )
    }
}
