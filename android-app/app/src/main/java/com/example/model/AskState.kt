package com.example.model

import android.graphics.Bitmap

/**
 * Represents the current stage of the Ask SeeAssist flow.
 */
sealed class AskState {
    object Idle : AskState()
    object RequestingPermission : AskState()
    object Listening : AskState()
    data class QuestionRecognized(val question: String) : AskState()
    data class FlushingCamera(val question: String) : AskState()
    data class CapturingFrames(val question: String, val currentFrame: Int, val totalFrames: Int = 5) : AskState()
    data class SelectingBestFrame(val question: String) : AskState()
    data class SendingToAI(val question: String, val bestFrameBitmap: Bitmap) : AskState()
    data class Analyzing(val question: String, val bestFrameBitmap: Bitmap) : AskState()
    data class Speaking(val question: String, val selectedAi: String, val answer: String, val bestFrameBitmap: Bitmap) : AskState()
    data class Success(
        val question: String,
        val selectedAi: String,
        val answer: String,
        val bestFrameBitmap: Bitmap,
        val frameScores: List<FrameScoreResult> = emptyList(),
        val selectedIndex: Int = 0
    ) : AskState()
    data class Error(
        val message: String,
        val stage: String,
        val canRetry: Boolean = true
    ) : AskState()
}

/**
 * Result of scoring a captured frame.
 */
data class FrameScoreResult(
    val frameIndex: Int,
    val sharpness: Float,
    val brightness: Float,
    val exposureScore: Float,
    val finalScore: Float
)
