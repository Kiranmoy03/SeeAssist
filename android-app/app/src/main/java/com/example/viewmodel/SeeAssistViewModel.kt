package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.BluetoothAudioManager
import com.example.audio.SpeechRecognitionManager
import com.example.audio.TextToSpeechManager
import com.example.camera.Esp32CameraClient
import com.example.camera.FrameQualitySelector
import com.example.model.AskState
import com.example.model.ConnectionStatus
import com.example.model.HandsFreePhase
import com.example.model.HandsFreeState
import com.example.model.WakeMicStatus
import com.example.model.WakeWordState
import com.example.network.SeeAssistBackendClient
import com.example.processor.SeeAssistQuestionProcessor
import com.example.service.HandsFreeService
import com.example.settings.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SeeAssistViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val esp32CameraClient = Esp32CameraClient()
    private val frameQualitySelector = FrameQualitySelector()
    private val backendClient = SeeAssistBackendClient()

    val bluetoothAudioManager = BluetoothAudioManager(application)
    val speechRecognitionManager = SpeechRecognitionManager(application)
    val textToSpeechManager = TextToSpeechManager(application)

    val esp32Ip: StateFlow<String> = settingsRepository.esp32IpFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsRepository.DEFAULT_ESP32_IP
    )

    val backendUrl: StateFlow<String> = settingsRepository.backendUrlFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsRepository.DEFAULT_BACKEND_URL
    )

    private val _askState = MutableStateFlow<AskState>(AskState.Idle)
    val askState: StateFlow<AskState> = _askState.asStateFlow()

    private val _esp32Status = MutableStateFlow<ConnectionStatus?>(null)
    val esp32Status: StateFlow<ConnectionStatus?> = _esp32Status.asStateFlow()

    private val _backendStatus = MutableStateFlow<ConnectionStatus?>(null)
    val backendStatus: StateFlow<ConnectionStatus?> = _backendStatus.asStateFlow()

    private val _isTestingEsp32 = MutableStateFlow(false)
    val isTestingEsp32: StateFlow<Boolean> = _isTestingEsp32.asStateFlow()

    private val _isTestingBackend = MutableStateFlow(false)
    val isTestingBackend: StateFlow<Boolean> = _isTestingBackend.asStateFlow()

    val handsFreeState: StateFlow<HandsFreeState> = HandsFreeService.handsFreeState
    val handsFreePhase: StateFlow<HandsFreePhase> = HandsFreeService.handsFreePhase
    val handsFreeErrorMessage: StateFlow<String?> = HandsFreeService.errorMessage
    val wakeWordState: StateFlow<WakeWordState> = HandsFreeService.wakeWordState
    val wakeWordDetectedPhrase: StateFlow<String?> = HandsFreeService.wakeWordDetectedPhrase
    val wakeMicStatus: StateFlow<WakeMicStatus> = HandsFreeService.wakeMicStatus

    private val questionProcessor = SeeAssistQuestionProcessor(
        esp32CameraClient = esp32CameraClient,
        frameQualitySelector = frameQualitySelector,
        backendClient = backendClient
    )

    private var currentExecutionJob: Job? = null

    init {
        // Initial quick background connectivity test
        refreshConnectionStatuses()

        // Sync with Hands-Free service ask state for real-time UI display
        viewModelScope.launch {
            HandsFreeService.latestAskState.collect { state ->
                if (state != null) {
                    _askState.value = state
                }
            }
        }
    }

    fun refreshConnectionStatuses() {
        testEsp32()
        testBackend()
    }

    fun testEsp32() {
        viewModelScope.launch {
            _isTestingEsp32.value = true
            val currentIp = esp32Ip.value
            val result = esp32CameraClient.testConnection(currentIp)
            _esp32Status.value = if (result.isSuccess) {
                ConnectionStatus(isConnected = true, message = result.getOrNull() ?: "ESP32 Connected")
            } else {
                ConnectionStatus(
                    isConnected = false,
                    message = result.exceptionOrNull()?.message ?: "Unable to connect to ESP32-CAM. Check Wi-Fi and IP address."
                )
            }
            _isTestingEsp32.value = false
        }
    }

    fun testBackend() {
        viewModelScope.launch {
            _isTestingBackend.value = true
            val currentUrl = backendUrl.value
            val status = backendClient.testConnection(currentUrl)
            _backendStatus.value = status
            _isTestingBackend.value = false
        }
    }

    fun saveSettings(newEsp32Ip: String, newBackendUrl: String) {
        viewModelScope.launch {
            settingsRepository.saveEsp32Ip(newEsp32Ip)
            settingsRepository.saveBackendUrl(newBackendUrl)
            testEsp32()
            testBackend()
        }
    }

    /**
     * Entry point for Ask SeeAssist button.
     * Prepares audio routing, then starts voice listening.
     */
    fun startAskFlow() {
        // Prevent concurrent executions
        if (_askState.value !is AskState.Idle &&
            _askState.value !is AskState.Success &&
            _askState.value !is AskState.Error
        ) {
            return
        }

        // Cancel previous job if running
        currentExecutionJob?.cancel()

        // Configure Bluetooth earbud microphone routing
        bluetoothAudioManager.updateAudioRouting()

        // Stop any currently playing TTS
        textToSpeechManager.stop()

        _askState.value = AskState.Listening

        speechRecognitionManager.startListening(
            onStatusChanged = { /* status handled by state */ },
            onResult = { recognizedText ->
                processQuestion(recognizedText)
            },
            onError = { errorMessage ->
                _askState.value = AskState.Error(
                    message = errorMessage,
                    stage = "Speech Recognition",
                    canRetry = true
                )
                // Speak error so visually impaired users hear what happened
                textToSpeechManager.speak(errorMessage)
            }
        )
    }

    /**
     * Executes the camera capture, quality selection, and AI querying pipeline.
     * Reuses the shared SeeAssistQuestionProcessor (identical to Hands-Free command execution).
     */
    fun processQuestion(question: String) {
        currentExecutionJob = viewModelScope.launch {
            val currentIp = esp32Ip.value
            val currentBackend = backendUrl.value

            val pipelineResult = questionProcessor.executePipeline(
                question = question,
                esp32Ip = currentIp,
                backendUrl = currentBackend,
                onProgress = { state ->
                    _askState.value = state
                }
            )

            if (pipelineResult.isFailure) {
                val ex = pipelineResult.exceptionOrNull()
                val spokenError = when {
                    ex?.message?.contains("Camera") == true -> "Cannot reach camera. Please check Wi-Fi."
                    ex?.message?.contains("AI backend") == true -> "Sorry, could not reach the SeeAssist backend server."
                    else -> "An error occurred. Please try again."
                }
                textToSpeechManager.speak(spokenError)
                return@launch
            }

            val result = pipelineResult.getOrThrow()
            _askState.value = AskState.Speaking(
                question = result.question,
                selectedAi = result.selectedAi,
                answer = result.answer,
                bestFrameBitmap = result.bestBitmap
            )

            // Speak answer automatically via Bluetooth earbud / audio output
            textToSpeechManager.speak(result.answer)

            _askState.value = AskState.Success(
                question = result.question,
                selectedAi = result.selectedAi,
                answer = result.answer,
                bestFrameBitmap = result.bestBitmap,
                frameScores = result.allScores,
                selectedIndex = result.selectedIndex
            )
        }
    }

    /**
     * Allows text-based questioning for manual testing or accessibility fallback.
     */
    fun askWithText(question: String) {
        if (question.isBlank()) return
        textToSpeechManager.stop()
        processQuestion(question.trim())
    }

    /**
     * Cancels active operations and returns to Idle.
     */
    fun cancelOrReset() {
        speechRecognitionManager.stopListening()
        textToSpeechManager.stop()
        currentExecutionJob?.cancel()
        currentExecutionJob = null
        _askState.value = AskState.Idle
    }

    /**
     * Starts the dedicated foreground microphone service for hands-free listening.
     */
    fun startHandsFree() {
        HandsFreeService.start(getApplication())
    }

    /**
     * Stops the hands-free foreground service and releases the microphone.
     */
    fun stopHandsFree() {
        HandsFreeService.stop(getApplication())
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognitionManager.destroy()
        textToSpeechManager.shutdown()
        bluetoothAudioManager.releaseRouting()
    }
}
