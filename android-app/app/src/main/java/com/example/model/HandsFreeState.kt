package com.example.model

/**
 * Represents the current operational state of Hands-Free Mode.
 *
 * Requirements:
 * - OFF: Microphone is released and service is stopped.
 * - STARTING: Service was launched from the UI, initializing notification and audio pipeline.
 * - LISTENING: AudioRecord successfully started capturing 16 kHz Mono PCM audio.
 * - ERROR: Failed initialization, missing permission, or microphone conflict.
 */
enum class HandsFreeState {
    OFF,
    STARTING,
    LISTENING,
    ERROR
}
