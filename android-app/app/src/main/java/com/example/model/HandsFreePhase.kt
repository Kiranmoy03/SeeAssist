package com.example.model

/**
 * Represents the distinct phases of Hands-Free Mode while the service is in LISTENING state.
 */
enum class HandsFreePhase {
    /**
     * Local sherpa-onnx keyword spotter is listening for "HEY SEE ASSIST".
     */
    LISTENING_FOR_WAKE_WORD,

    /**
     * Wake word was spotted, AudioRecord paused, prompt sounded, now listening for user's voice command.
     */
    LISTENING_FOR_COMMAND,

    /**
     * Command received: capturing ESP32 frames, selecting best frame, and querying AI backend.
     */
    PROCESSING,

    /**
     * AI answer received: speaking the answer via TextToSpeech.
     */
    SPEAKING
}
