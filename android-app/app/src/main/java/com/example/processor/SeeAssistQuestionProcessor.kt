package com.example.processor

import android.graphics.Bitmap
import android.util.Log
import com.example.camera.Esp32CameraClient
import com.example.camera.FrameQualitySelector
import com.example.model.AskState
import com.example.model.FrameScoreResult
import com.example.network.SeeAssistBackendClient

/**
 * Result data holder for a processed SeeAssist question.
 */
data class ProcessedQuestionResult(
    val question: String,
    val answer: String,
    val selectedAi: String,
    val bestBitmap: Bitmap,
    val allScores: List<FrameScoreResult>,
    val selectedIndex: Int
)

/**
 * Shared question processing pipeline used by both:
 * A) The normal "Ask SeeAssist" UI button
 * B) Hands-Free wake-word voice command execution
 *
 * Ensures identical:
 * - 2 stale-frame discards
 * - 5 fresh-frame captures
 * - Laplacian blur + contrast quality scoring
 * - Gradio / AI backend request handling
 */
class SeeAssistQuestionProcessor(
    private val esp32CameraClient: Esp32CameraClient = Esp32CameraClient(),
    private val frameQualitySelector: FrameQualitySelector = FrameQualitySelector(),
    private val backendClient: SeeAssistBackendClient = SeeAssistBackendClient()
) {
    companion object {
        private const val TAG = "SeeAssistProcessor"
    }

    suspend fun executePipeline(
        question: String,
        esp32Ip: String,
        backendUrl: String,
        onProgress: (AskState) -> Unit = {}
    ): Result<ProcessedQuestionResult> {
        Log.i(TAG, "Starting SeeAssist Ask pipeline for question: \"$question\"")
        onProgress(AskState.QuestionRecognized(question))

        // 1. ESP32 Buffer Flushing (2 discarded frames to clear hardware cache)
        onProgress(AskState.FlushingCamera(question))
        val flushResult = esp32CameraClient.flushStaleFrames(esp32Ip)
        if (flushResult.isFailure) {
            val err = "Camera is not connected."
            Log.e(TAG, "ESP32 flush failed: ${flushResult.exceptionOrNull()?.message}")
            onProgress(AskState.Error(message = err, stage = "ESP32 Camera"))
            return Result.failure(Exception(err, flushResult.exceptionOrNull()))
        }

        // 2. Five-Frame Capture
        onProgress(AskState.CapturingFrames(question, 1, 5))
        val captureResult = esp32CameraClient.captureFiveFrames(esp32Ip) { current, total ->
            onProgress(AskState.CapturingFrames(question, current, total))
        }

        if (captureResult.isFailure) {
            val err = "Camera capture failed."
            Log.e(TAG, "Capture failed: ${captureResult.exceptionOrNull()?.message}")
            onProgress(AskState.Error(message = err, stage = "Camera Capture"))
            return Result.failure(Exception(err, captureResult.exceptionOrNull()))
        }

        val frames = captureResult.getOrThrow()

        // 3. Best-Frame Quality Scoring
        onProgress(AskState.SelectingBestFrame(question))
        val selectionResult = frameQualitySelector.selectBestFrame(frames)
        if (selectionResult.isFailure) {
            val err = "Failed to evaluate camera frame."
            Log.e(TAG, "Frame selection failed: ${selectionResult.exceptionOrNull()?.message}")
            onProgress(AskState.Error(message = err, stage = "Frame Selection"))
            return Result.failure(Exception(err, selectionResult.exceptionOrNull()))
        }

        val bestSelection = selectionResult.getOrThrow()

        // 4. Send to SeeAssist AI Backend
        onProgress(AskState.SendingToAI(question, bestSelection.frame.bitmap))
        onProgress(AskState.Analyzing(question, bestSelection.frame.bitmap))

        val aiResult = backendClient.querySeeAssist(
            baseUrl = backendUrl,
            imageBytes = bestSelection.frame.jpegBytes,
            question = question
        )

        if (aiResult.isFailure) {
            val err = "AI backend is not available."
            Log.e(TAG, "Backend error: ${aiResult.exceptionOrNull()?.message}")
            onProgress(AskState.Error(message = err, stage = "AI Backend"))
            return Result.failure(Exception(err, aiResult.exceptionOrNull()))
        }

        val backendResult = aiResult.getOrThrow()
        Log.i(TAG, "SeeAssist AI response received: \"${backendResult.answer}\" (Model: ${backendResult.selectedAi})")

        val result = ProcessedQuestionResult(
            question = question,
            answer = backendResult.answer,
            selectedAi = backendResult.selectedAi,
            bestBitmap = bestSelection.frame.bitmap,
            allScores = bestSelection.allScores,
            selectedIndex = bestSelection.score.frameIndex
        )

        return Result.success(result)
    }
}
