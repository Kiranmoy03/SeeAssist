package com.example.model

/**
 * Result returned from the SeeAssist AI backend.
 *
 * @param selectedAi Name/type of the selected model (e.g. "VQA", "OCR", "OBJECT_DETECTION")
 * @param answer Spoken/textual answer
 * @param rawResponse Optional raw debugging text
 */
data class BackendResult(
    val selectedAi: String,
    val answer: String,
    val rawResponse: String? = null
)

/**
 * Connection check status.
 */
data class ConnectionStatus(
    val isConnected: Boolean,
    val message: String,
    val lastCheckedTimestamp: Long = System.currentTimeMillis()
)
