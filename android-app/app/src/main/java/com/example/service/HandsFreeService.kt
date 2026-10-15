package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.audio.SpeechRecognitionManager
import com.example.audio.TextToSpeechManager
import com.example.model.AskState
import com.example.model.HandsFreePhase
import com.example.model.HandsFreeState
import com.example.model.WakeMicRouteType
import com.example.model.WakeMicStatus
import com.example.model.WakeWordState
import com.example.processor.SeeAssistQuestionProcessor
import com.example.settings.SettingsRepository
import com.example.wakeword.SherpaWakeWordDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Dedicated Foreground Service for Hands-Free background microphone listening.
 *
 * Implements reliable Bluetooth communication microphone routing:
 * - Prioritizes TYPE_BLUETOOTH_SCO and TYPE_BLE_HEADSET communication devices
 * - Re-asserts MODE_IN_COMMUNICATION and setCommunicationDevice on start, resume, and reconnect
 * - Applies setPreferredDevice with VOICE_COMMUNICATION source to AudioRecord
 * - Verifies AudioRecord.routedDevice and exposes actual WakeMicStatus
 * - Retains Bluetooth route during Android SpeechRecognizer execution
 * - Dynamically recovers from device connection changes via AudioDeviceCallback
 * - Monitors read health and logs diagnostic audio levels every ~2 seconds
 */
class HandsFreeService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var recordingJob: Job? = null
    private var processingJob: Job? = null

    private var audioRecord: AudioRecord? = null
    @Volatile
    private var isRecording = false
    @Volatile
    private var isHandsFreeActive = false

    private val wakeWordDetector = SherpaWakeWordDetector()
    private var speechRecognitionManager: SpeechRecognitionManager? = null
    private var textToSpeechManager: TextToSpeechManager? = null
    private lateinit var settingsRepository: SettingsRepository
    private val questionProcessor = SeeAssistQuestionProcessor()

    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private var previousAudioMode: Int = AudioManager.MODE_NORMAL

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            val hasBt = addedDevices?.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            } == true
            if (hasBt) {
                Log.i(TAG, "Bluetooth headset connected while Hands-Free is active! Re-routing audio...")
                serviceScope.launch(Dispatchers.Main) {
                    handleBluetoothDeviceConnected()
                }
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            val hadBt = removedDevices?.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            } == true
            if (hadBt) {
                Log.i(TAG, "Bluetooth headset disconnected while Hands-Free is active! Falling back to phone mic...")
                serviceScope.launch(Dispatchers.Main) {
                    handleBluetoothDeviceDisconnected()
                }
            }
        }
    }

    companion object {
        private const val TAG = "HandsFreeService"
        const val ACTION_START = "com.example.service.action.START"
        const val ACTION_STOP = "com.example.service.action.STOP"

        private const val NOTIFICATION_CHANNEL_ID = "seeassist_hands_free_channel"
        private const val NOTIFICATION_ID = 1001

        // Audio specifications for wake-word compatibility
        const val SAMPLE_RATE_HZ = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        private val _handsFreeState = MutableStateFlow(HandsFreeState.OFF)
        val handsFreeState: StateFlow<HandsFreeState> = _handsFreeState.asStateFlow()

        private val _handsFreePhase = MutableStateFlow(HandsFreePhase.LISTENING_FOR_WAKE_WORD)
        val handsFreePhase: StateFlow<HandsFreePhase> = _handsFreePhase.asStateFlow()

        private val _errorMessage = MutableStateFlow<String?>(null)
        val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

        private val _wakeWordState = MutableStateFlow(WakeWordState.NONE)
        val wakeWordState: StateFlow<WakeWordState> = _wakeWordState.asStateFlow()

        private val _wakeWordDetectedPhrase = MutableStateFlow<String?>(null)
        val wakeWordDetectedPhrase: StateFlow<String?> = _wakeWordDetectedPhrase.asStateFlow()

        private val _latestAskState = MutableStateFlow<AskState?>(null)
        val latestAskState: StateFlow<AskState?> = _latestAskState.asStateFlow()

        private val _wakeMicStatus = MutableStateFlow(WakeMicStatus())
        val wakeMicStatus: StateFlow<WakeMicStatus> = _wakeMicStatus.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, HandsFreeService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, HandsFreeService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        previousAudioMode = audioManager.mode
        settingsRepository = SettingsRepository(applicationContext)
        textToSpeechManager = TextToSpeechManager(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.i(TAG, "Stop action received in onStartCommand")
                stopHandsFreeListening()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                Log.i(TAG, "Start action received in onStartCommand")
                startHandsFreeListening()
            }
            else -> {
                if (_handsFreeState.value == HandsFreeState.OFF) {
                    startHandsFreeListening()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startHandsFreeListening() {
        _handsFreeState.value = HandsFreeState.STARTING
        _errorMessage.value = null

        // Verify microphone permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            val err = "Microphone permission (RECORD_AUDIO) is not granted"
            Log.e(TAG, err)
            _errorMessage.value = err
            _handsFreeState.value = HandsFreeState.ERROR
            stopSelf()
            return
        }

        // Attach persistent notification before starting foreground execution
        try {
            val notification = buildForegroundNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            val err = "Failed to start foreground service notification: ${e.message}"
            Log.e(TAG, err, e)
            _errorMessage.value = err
            _handsFreeState.value = HandsFreeState.ERROR
            stopSelf()
            return
        }

        // 1. Initialize Sherpa-ONNX KeywordSpotter FIRST before starting microphone capture
        val spotterInit = try {
            wakeWordDetector.initialize(assets)
        } catch (t: Throwable) {
            Log.e(TAG, "Uncaught Throwable initializing KeywordSpotter", t)
            Result.failure(t)
        }

        if (spotterInit.isFailure) {
            val err = spotterInit.exceptionOrNull()?.message ?: "KeywordSpotter initialization failed"
            Log.e(TAG, "SherpaOnnx KWS init failed: $err", spotterInit.exceptionOrNull())
            _errorMessage.value = err
            _handsFreeState.value = HandsFreeState.ERROR
            wakeWordDetector.release()
            stopForegroundSafely()
            stopSelf()
            return
        }

        // Register dynamic audio device callback to handle headset disconnects/reconnects
        try {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            Log.w(TAG, "Error registering AudioDeviceCallback: ${e.message}")
        }

        // 2. Configure Bluetooth communication route and instantiate AudioRecord
        val recordReady = createAndStartAudioRecord(isResume = false)
        if (!recordReady) {
            wakeWordDetector.release()
            stopForegroundSafely()
            stopSelf()
            return
        }

        // Both KWS and AudioRecord are ready: enter LISTENING state!
        isHandsFreeActive = true
        isRecording = true
        _handsFreeState.value = HandsFreeState.LISTENING
        _handsFreePhase.value = HandsFreePhase.LISTENING_FOR_WAKE_WORD
        _errorMessage.value = null

        // 3. Start audio read loop in background coroutine
        startAudioReadLoop()
    }

    /**
     * Inspects available communication devices and requests Bluetooth communication routing.
     * Does NOT treat TYPE_BLUETOOTH_A2DP as a communication microphone.
     */
    private fun setupCommunicationAudioRoute(): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val commDevices = audioManager.availableCommunicationDevices
                val btCommDevice = commDevices.firstOrNull { dev ->
                    dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }

                if (btCommDevice != null) {
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    val success = audioManager.setCommunicationDevice(btCommDevice)
                    Log.i(
                        TAG,
                        "Bluetooth communication route requested: ${btCommDevice.productName} (type: ${btCommDevice.type})\nsetCommunicationDevice result: $success"
                    )
                    return if (success) btCommDevice else null
                } else {
                    Log.i(TAG, "No Bluetooth communication device found in availableCommunicationDevices (total: ${commDevices.size})")
                    // If no Bluetooth communication device is present, set mode to communication anyway for consistent audio processing
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error asserting setCommunicationDevice: ${e.message}")
            }
        } else {
            @Suppress("DEPRECATION")
            try {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                if (audioManager.isBluetoothScoAvailableOffCall) {
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                    Log.i(TAG, "Legacy Bluetooth SCO started. isBluetoothScoOn: ${audioManager.isBluetoothScoOn}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error starting legacy Bluetooth SCO: ${e.message}")
            }
        }
        return null
    }

    /**
     * Finds an actual Bluetooth microphone input device from GET_DEVICES_INPUTS.
     */
    private fun findBluetoothInputDevice(): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val inputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                return inputDevices.firstOrNull { dev ->
                    dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error querying GET_DEVICES_INPUTS: ${e.message}")
            }
        }
        return null
    }

    /**
     * Creates and starts AudioRecord, preferring VOICE_COMMUNICATION and Bluetooth preferred device.
     */
    private fun createAndStartAudioRecord(isResume: Boolean): Boolean {
        Log.i(TAG, "Configuring AudioRecord and Bluetooth route (isResume=$isResume)")

        // 1. Assert communication audio route
        val btCommDevice = setupCommunicationAudioRoute()
        val btInput = findBluetoothInputDevice()

        // 2. Buffer size calculation
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize <= 0) {
            val err = "Invalid AudioRecord minBufferSize for 16kHz Mono PCM 16-bit: $minBufferSize"
            Log.e(TAG, err)
            _errorMessage.value = err
            _handsFreeState.value = HandsFreeState.ERROR
            return false
        }
        val bufferSize = max(minBufferSize * 2, SAMPLE_RATE_HZ * 2 / 5)

        // Release any existing AudioRecord instance cleanly
        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (t: Throwable) {
            // Ignore
        }
        audioRecord = null

        // 3. AudioSource: Prefer VOICE_COMMUNICATION for Bluetooth communication sessions
        audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE_HZ,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
        } catch (e: Exception) {
            Log.w(TAG, "VOICE_COMMUNICATION source failed: ${e.message}, trying VOICE_RECOGNITION")
            try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE_HZ,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
            } catch (e2: Exception) {
                Log.w(TAG, "VOICE_RECOGNITION failed: ${e2.message}, falling back to MIC")
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE_HZ,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
            }
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord?.release()
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE_HZ,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            val err = "AudioRecord failed to initialize at 16000 Hz Mono PCM 16-bit"
            Log.e(TAG, err)
            _errorMessage.value = err
            _handsFreeState.value = HandsFreeState.ERROR
            return false
        }

        // 4. Apply setPreferredDevice if Bluetooth input device is available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && btInput != null) {
            val prefResult = audioRecord?.setPreferredDevice(btInput) ?: false
            Log.i(
                TAG,
                "Preferred wake microphone:\nname=${btInput.productName}\ntype=${btInput.type}\nsetPreferredDevice=$prefResult"
            )
        } else {
            Log.i(TAG, "No Bluetooth input device found for setPreferredDevice; using communication device default")
        }

        // 5. Attach routing changed listener on API 24+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            audioRecord?.addOnRoutingChangedListener({ rec ->
                updateActualWakeMicrophone(rec.routedDevice)
            }, Handler(Looper.getMainLooper()))
        }

        // 6. Start AudioRecord
        audioRecord?.startRecording()

        // 7. Verify recordingState
        if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            val err = "AudioRecord failed to enter recording state (state: ${audioRecord?.recordingState})"
            Log.e(TAG, err)
            _errorMessage.value = err
            _handsFreeState.value = HandsFreeState.ERROR
            return false
        }

        // 8. Verify routedDevice
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            updateActualWakeMicrophone(audioRecord?.routedDevice)
        }

        return true
    }

    /**
     * Inspects the ACTUAL routed device on AudioRecord and updates WakeMicStatus.
     */
    private fun updateActualWakeMicrophone(device: AudioDeviceInfo?) {
        if (device == null) {
            Log.d(TAG, "AudioRecord routedDevice is pending HAL allocation.")
            return
        }

        Log.i(TAG, "ACTUAL WAKE MICROPHONE:\n${device.productName}\ntype=${device.type}")

        val isBt = (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || device.type == AudioDeviceInfo.TYPE_BLE_HEADSET)

        if (isBt) {
            _wakeMicStatus.value = WakeMicStatus(
                routeType = WakeMicRouteType.BLUETOOTH,
                deviceName = device.productName.toString(),
                deviceType = device.type,
                isBluetooth = true,
                description = "${device.productName} (Bluetooth)"
            )
        } else {
            val hasBtDevice = findBluetoothInputDevice() != null
            if (hasBtDevice) {
                Log.w(TAG, "Bluetooth microphone routing failed - using phone microphone.")
                _wakeMicStatus.value = WakeMicStatus(
                    routeType = WakeMicRouteType.PHONE_MIC,
                    deviceName = device.productName.toString(),
                    deviceType = device.type,
                    isBluetooth = false,
                    description = "Phone microphone (fallback)"
                )
            } else {
                _wakeMicStatus.value = WakeMicStatus(
                    routeType = WakeMicRouteType.PHONE_MIC,
                    deviceName = device.productName.toString(),
                    deviceType = device.type,
                    isBluetooth = false,
                    description = "Phone microphone"
                )
            }
        }
    }

    private fun handleBluetoothDeviceConnected() {
        if (!isHandsFreeActive) return
        Log.i(TAG, "Handling Bluetooth device connection in active Hands-Free session")
        setupCommunicationAudioRoute()
        if (_handsFreePhase.value == HandsFreePhase.LISTENING_FOR_WAKE_WORD) {
            resumeWakeWordListening()
        }
    }

    private fun handleBluetoothDeviceDisconnected() {
        if (!isHandsFreeActive) return
        Log.i(TAG, "Handling Bluetooth device disconnection in active Hands-Free session")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                audioManager.clearCommunicationDevice()
            } catch (e: Exception) {
                // Ignore
            }
        }
        _wakeMicStatus.value = WakeMicStatus(
            routeType = WakeMicRouteType.PHONE_MIC,
            deviceName = "Phone microphone",
            deviceType = AudioDeviceInfo.TYPE_BUILTIN_MIC,
            isBluetooth = false,
            description = "Phone microphone (Bluetooth disconnected)"
        )
        if (_handsFreePhase.value == HandsFreePhase.LISTENING_FOR_WAKE_WORD) {
            resumeWakeWordListening()
        }
    }

    private fun startAudioReadLoop() {
        recordingJob?.cancel()
        recordingJob = serviceScope.launch(Dispatchers.IO) {
            val buffer = ShortArray(1024)
            var consecutiveErrors = 0
            var recoveryAttempts = 0
            var lastAudioLevelLogTime = 0L

            Log.d(TAG, "Audio read loop started with local SherpaOnnx wake-word detection (16kHz mono)")

            while (isRecording && isActive) {
                // Verify AudioRecord health
                if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    consecutiveErrors++
                    Log.w(TAG, "AudioRecord dropped recordingState: ${audioRecord?.recordingState} (consecutive: $consecutiveErrors)")
                }

                val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: -1

                if (readCount > 0) {
                    consecutiveErrors = 0
                    recoveryAttempts = 0

                    // Diagnostic audio level logging every ~2 seconds (Requirement 9)
                    val now = System.currentTimeMillis()
                    if (now - lastAudioLevelLogTime >= 2000L) {
                        lastAudioLevelLogTime = now
                        var sum = 0.0
                        for (i in 0 until readCount) {
                            sum += abs(buffer[i].toDouble())
                        }
                        val avgAmp = (sum / readCount).toInt()
                        val currentRoute = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            audioRecord?.routedDevice?.let { "${it.productName} (type: ${it.type})" } ?: "Unresolved"
                        } else "Default"
                        Log.d(TAG, "Wake audio level: $avgAmp | Route: $currentRoute")
                    }

                    // Feed to Sherpa-ONNX KeywordSpotter
                    val detected = wakeWordDetector.processAudioSamples(buffer, readCount)
                    if (detected != null) {
                        Log.i(TAG, "Wake word detected: HEY SEE ASSIST ($detected)")
                        handleWakeWordDetected(detected)
                        break // Break read loop as AudioRecord is surrendered for speech recognition
                    }
                } else if (readCount < 0) {
                    consecutiveErrors++
                    Log.w(TAG, "AudioRecord read returned error code: $readCount (consecutive: $consecutiveErrors)")
                }

                // Bounded error recovery if read fails repeatedly (Requirement 9)
                if (consecutiveErrors >= 3 && isRecording && isActive) {
                    consecutiveErrors = 0
                    if (recoveryAttempts < 3) {
                        recoveryAttempts++
                        Log.e(TAG, "AudioRecord read failed repeatedly; attempting bounded recovery ($recoveryAttempts/3)")
                        delay(300)
                        val recovered = createAndStartAudioRecord(isResume = true)
                        if (recovered) {
                            wakeWordDetector.resetStream()
                            Log.i(TAG, "AudioRecord recovery attempt $recoveryAttempts succeeded")
                        } else {
                            Log.e(TAG, "AudioRecord recovery attempt $recoveryAttempts failed")
                        }
                    } else {
                        Log.e(TAG, "AudioRecord exceeded max recovery attempts (3). Halting read loop.")
                        _errorMessage.value = "Microphone capture failure"
                        _handsFreeState.value = HandsFreeState.ERROR
                        break
                    }
                }
            }
            Log.d(TAG, "Audio read loop paused/finished")
        }
    }

    /**
     * Handles wake word detection:
     * 1. Pauses and releases AudioRecord so microphone is free.
     * 2. KEEPS Bluetooth communication route active so SpeechRecognizer uses earbuds!
     * 3. Plays prompt "Yes?" via TTS.
     * 4. Once prompt finishes (via onDone), starts one-shot SpeechRecognizer on Main thread.
     */
    private fun handleWakeWordDetected(keyword: String) {
        Log.i(TAG, "Wake word detected: HEY SEE ASSIST ($keyword). Pausing AudioRecord for SpeechRecognizer.")
        _wakeWordState.value = WakeWordState.DETECTED
        _wakeWordDetectedPhrase.value = "SeeAssist heard you"

        // 1. Immediately pause and release AudioRecord microphone ownership
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (e: Throwable) {
            Log.w(TAG, "Error stopping/releasing AudioRecord: ${e.message}")
        }
        audioRecord = null
        Log.i(TAG, "AudioRecord released - microphone free for SpeechRecognizer (Bluetooth route retained)")

        // Update phase
        _handsFreePhase.value = HandsFreePhase.LISTENING_FOR_COMMAND

        // Clear transient DETECTED badge after 1.5 seconds
        serviceScope.launch {
            delay(1500)
            _wakeWordState.value = WakeWordState.NONE
            _wakeWordDetectedPhrase.value = null
        }

        // 2. Play short feedback prompt ("Yes?") so user knows to speak.
        // SpeechRecognizer MUST NOT start until this feedback has finished!
        serviceScope.launch(Dispatchers.Main) {
            if (!isHandsFreeActive) return@launch

            textToSpeechManager?.speak("Yes?", onDone = {
                Log.i(TAG, "Prompt 'Yes?' finished speaking. Starting SpeechRecognizer.")
                serviceScope.launch(Dispatchers.Main) {
                    if (!isHandsFreeActive) return@launch
                    startCommandRecognition()
                }
            })
        }
    }

    /**
     * Starts one-shot SpeechRecognizer on Main thread while retaining Bluetooth communication route.
     */
    private fun startCommandRecognition() {
        if (!isHandsFreeActive) return
        Log.i(TAG, "Starting one-shot SpeechRecognizer on Main thread with Bluetooth communication route active")
        _handsFreePhase.value = HandsFreePhase.LISTENING_FOR_COMMAND

        try {
            speechRecognitionManager?.destroy()
            speechRecognitionManager = SpeechRecognitionManager(applicationContext).apply {
                startListening(
                    onStatusChanged = { status ->
                        Log.d(TAG, "SpeechRecognizer status: $status")
                    },
                    onResult = { recognizedQuestion ->
                        Log.i(TAG, "Command recognized: \"$recognizedQuestion\"")
                        speechRecognitionManager?.destroy()
                        speechRecognitionManager = null
                        handleRecognizedCommand(recognizedQuestion)
                    },
                    onError = { errorMessage ->
                        Log.w(TAG, "SpeechRecognizer error: $errorMessage")
                        speechRecognitionManager?.destroy()
                        speechRecognitionManager = null
                        // Resume wake-word listening without terminating hands-free mode
                        resumeWakeWordListening()
                    }
                )
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error starting SpeechRecognizer: ${t.message}", t)
            resumeWakeWordListening()
        }
    }

    /**
     * Routes recognized voice commands:
     * - Navigation commands: "start navigation" / "stop navigation"
     * - Visual questions: processed through shared SeeAssistQuestionProcessor
     */
    private fun handleRecognizedCommand(command: String) {
        if (!isHandsFreeActive) return

        val cleanCmd = command.trim().lowercase(Locale.getDefault())

        // 1. Navigation Commands (Day 3 stubs)
        if (cleanCmd.contains("start navigation")) {
            speakResponseAndResume("Navigation mode is not implemented yet.")
            return
        } else if (cleanCmd.contains("stop navigation")) {
            speakResponseAndResume("Navigation mode is not currently active.")
            return
        }

        // 2. Visual Questions
        _handsFreePhase.value = HandsFreePhase.PROCESSING
        processingJob?.cancel()
        processingJob = serviceScope.launch {
            val ip = settingsRepository.esp32IpFlow.first()
            val backend = settingsRepository.backendUrlFlow.first()

            val pipelineResult = questionProcessor.executePipeline(
                question = command,
                esp32Ip = ip,
                backendUrl = backend,
                onProgress = { askState ->
                    _latestAskState.value = askState
                }
            )

            if (!isHandsFreeActive) return@launch

            if (pipelineResult.isFailure) {
                val ex = pipelineResult.exceptionOrNull()
                val spokenError = when {
                    ex?.message?.contains("Camera is not connected") == true -> "Camera is not connected."
                    ex?.message?.contains("AI backend is not available") == true -> "AI backend is not available."
                    else -> "Sorry, could not complete request."
                }
                Log.e(TAG, "Pipeline error: ${ex?.message}. Speaking: \"$spokenError\"")
                speakResponseAndResume(spokenError)
            } else {
                val result = pipelineResult.getOrThrow()
                _latestAskState.value = AskState.Success(
                    question = result.question,
                    selectedAi = result.selectedAi,
                    answer = result.answer,
                    bestFrameBitmap = result.bestBitmap,
                    frameScores = result.allScores,
                    selectedIndex = result.selectedIndex
                )
                speakResponseAndResume(result.answer)
            }
        }
    }

    /**
     * Speaks the answer/error aloud and, ONLY after TTS has finished speaking (onDone),
     * automatically resumes wake-word listening.
     */
    private fun speakResponseAndResume(speechText: String) {
        if (!isHandsFreeActive) return
        _handsFreePhase.value = HandsFreePhase.SPEAKING
        Log.i(TAG, "Speaking response: \"$speechText\"")

        serviceScope.launch(Dispatchers.Main) {
            textToSpeechManager?.speak(speechText, onDone = {
                Log.i(TAG, "TTS finished speaking response (onDone). Returning to wake-word listening.")
                resumeWakeWordListening()
            })
        }
    }

    /**
     * Recreates AudioRecord and resets the OnlineStream to resume wake-word listening.
     * Executes the strict 11-step sequence:
     * 1. verify Bluetooth device is still connected/available
     * 2. re-assert MODE_IN_COMMUNICATION
     * 3. re-assert setCommunicationDevice()
     * 4. recreate AudioRecord with VOICE_COMMUNICATION
     * 5. re-apply setPreferredDevice()
     * 6. start AudioRecord
     * 7. verify recordingState
     * 8. verify routedDevice
     * 9. reset/create sherpa OnlineStream
     * 10. restart PCM read loop
     * 11. only then report LISTENING_FOR_WAKE_WORD
     */
    private fun resumeWakeWordListening() {
        serviceScope.launch {
            if (!isHandsFreeActive || _handsFreeState.value != HandsFreeState.LISTENING) {
                Log.d(TAG, "Hands-Free is not active; skipping resumeWakeWordListening")
                return@launch
            }

            Log.i(TAG, "Resuming wake-word listening with full Bluetooth routing re-assertion")

            // Steps 1 to 8: Bluetooth check, MODE_IN_COMMUNICATION, setCommunicationDevice, AudioRecord recreate, setPreferredDevice, start, verify
            val recordReady = createAndStartAudioRecord(isResume = true)
            if (!recordReady) {
                Log.e(TAG, "Failed to re-initialize and start AudioRecord on resume")
                return@launch
            }

            // Step 9: Reset sherpa OnlineStream
            wakeWordDetector.resetStream()

            // Step 10: Restart PCM read loop
            isRecording = true
            _errorMessage.value = null
            startAudioReadLoop()

            // Step 11: Only then report LISTENING_FOR_WAKE_WORD!
            _handsFreePhase.value = HandsFreePhase.LISTENING_FOR_WAKE_WORD
            Log.i(TAG, "Wake-word listening resumed successfully. State: LISTENING, Phase: LISTENING_FOR_WAKE_WORD")
        }
    }

    /**
     * Safely stops hands-free listening from ANY active phase and restores audio configuration.
     */
    private fun stopHandsFreeListening() {
        Log.i(TAG, "Stopping Hands-Free Service during phase: ${_handsFreePhase.value}")
        isHandsFreeActive = false
        isRecording = false

        // Unregister AudioDeviceCallback
        try {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering audioDeviceCallback: ${e.message}")
        }

        // Clear communication device (Requirement 10)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                audioManager.clearCommunicationDevice()
                Log.i(TAG, "Cleared communication device")
            } catch (e: Exception) {
                Log.w(TAG, "Error clearing communication device: ${e.message}")
            }
        } else {
            @Suppress("DEPRECATION")
            try {
                if (audioManager.isBluetoothScoOn) {
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping legacy Bluetooth SCO: ${e.message}")
            }
        }

        // Restore previous AudioManager mode
        try {
            audioManager.mode = previousAudioMode
            Log.i(TAG, "Restored AudioManager mode to $previousAudioMode")
        } catch (e: Exception) {
            Log.w(TAG, "Error restoring audio mode: ${e.message}")
        }

        recordingJob?.cancel()
        recordingJob = null
        processingJob?.cancel()
        processingJob = null

        try {
            speechRecognitionManager?.destroy()
        } catch (t: Throwable) {
            Log.w(TAG, "Error destroying speech recognizer: ${t.message}")
        }
        speechRecognitionManager = null

        try {
            textToSpeechManager?.stop()
        } catch (t: Throwable) {
            Log.w(TAG, "Error stopping TTS: ${t.message}")
        }

        wakeWordDetector.release()
        _wakeWordState.value = WakeWordState.NONE
        _wakeWordDetectedPhrase.value = null

        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord: ${e.message}")
        }

        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioRecord: ${e.message}")
        }
        audioRecord = null

        _handsFreeState.value = HandsFreeState.OFF
        _handsFreePhase.value = HandsFreePhase.LISTENING_FOR_WAKE_WORD
        _errorMessage.value = null
        _wakeMicStatus.value = WakeMicStatus()

        stopForegroundSafely()
        Log.i(TAG, "Hands-Free Service stopped cleanly. State: OFF, Wake Mic: Inactive")
    }

    private fun stopForegroundSafely() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping foreground notification: ${e.message}")
        }
    }

    private fun buildForegroundNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, HandsFreeService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("SeeAssist Hands-Free")
            .setContentText("Listening for the SeeAssist wake word")
            .setSubText("Microphone Active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop",
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "SeeAssist Hands-Free",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active status when SeeAssist hands-free microphone listening is enabled"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopHandsFreeListening()
        textToSpeechManager?.shutdown()
        textToSpeechManager = null
        serviceScope.cancel()
        super.onDestroy()
    }
}
