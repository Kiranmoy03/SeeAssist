package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.model.AskState
import com.example.model.ConnectionStatus
import com.example.model.FrameScoreResult
import com.example.model.HandsFreePhase
import com.example.model.HandsFreeState
import com.example.model.WakeMicStatus
import com.example.model.WakeWordState
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.viewmodel.SeeAssistViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: SeeAssistViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToNavigation: () -> Unit
) {
    val context = LocalContext.current
    val askState by viewModel.askState.collectAsState()
    val esp32Status by viewModel.esp32Status.collectAsState()
    val backendStatus by viewModel.backendStatus.collectAsState()
    val isTestingEsp32 by viewModel.isTestingEsp32.collectAsState()
    val isTestingBackend by viewModel.isTestingBackend.collectAsState()
    val isBluetoothConnected by viewModel.bluetoothAudioManager.isBluetoothConnected.collectAsState()
    val audioRouteDesc by viewModel.bluetoothAudioManager.audioRouteDescription.collectAsState()
    val handsFreeState by viewModel.handsFreeState.collectAsState()
    val handsFreePhase by viewModel.handsFreePhase.collectAsState()
    val handsFreeErrorMessage by viewModel.handsFreeErrorMessage.collectAsState()
    val wakeWordState by viewModel.wakeWordState.collectAsState()
    val wakeWordDetectedPhrase by viewModel.wakeWordDetectedPhrase.collectAsState()
    val wakeMicStatus by viewModel.wakeMicStatus.collectAsState()

    var showManualInput by remember { mutableStateOf(false) }
    var manualQuestionText by remember { mutableStateOf("") }

    // Audio & Bluetooth permission launcher for Ask mode
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (audioGranted) {
            viewModel.startAskFlow()
        }
    }

    // Permission launcher for Hands-Free foreground service (Audio + Bluetooth + Notifications)
    val handsFreePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (audioGranted) {
            viewModel.startHandsFree()
        }
    }

    fun handleAskClick() {
        val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        val missingPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            viewModel.startAskFlow()
        } else {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    fun handleStartHandsFree() {
        val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            viewModel.startHandsFree()
        } else {
            handsFreePermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "SeeAssist",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "AI Visual Assistant for Visually Impaired",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .testTag("settings_button")
                            .semantics { contentDescription = "Open Settings screen" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Section: Hardware & Backend Connectivity Indicators
            StatusOverviewCard(
                esp32Status = esp32Status,
                backendStatus = backendStatus,
                isTestingEsp32 = isTestingEsp32,
                isTestingBackend = isTestingBackend,
                isBluetooth = isBluetoothConnected,
                audioRouteDesc = audioRouteDesc,
                onRefresh = { viewModel.refreshConnectionStatuses() }
            )

            // Hands-Free Mode Control (Dedicated Foreground Microphone Service with SherpaOnnx wake-word)
            HandsFreeControlCard(
                handsFreeState = handsFreeState,
                handsFreePhase = handsFreePhase,
                errorMessage = handsFreeErrorMessage,
                wakeWordState = wakeWordState,
                wakeWordPhrase = wakeWordDetectedPhrase,
                wakeMicStatus = wakeMicStatus,
                onStart = { handleStartHandsFree() },
                onStop = { viewModel.stopHandsFree() }
            )

            // Primary Interaction Button: "Ask SeeAssist"
            PrimaryAskButton(
                askState = askState,
                onAskClick = { handleAskClick() },
                onCancelClick = { viewModel.cancelOrReset() }
            )

            // Result or Active State Feedback
            when (val state = askState) {
                is AskState.Idle -> {
                    Text(
                        text = "Press the large button above to ask a question using your Bluetooth earbud or phone mic.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                is AskState.Listening,
                is AskState.FlushingCamera,
                is AskState.CapturingFrames,
                is AskState.SelectingBestFrame,
                is AskState.SendingToAI,
                is AskState.Analyzing,
                is AskState.Speaking -> {
                    ProgressDisplayCard(askState = state)
                }

                is AskState.Success -> {
                    ResultDisplayCard(
                        successState = state,
                        onReplaySpeech = { viewModel.textToSpeechManager.speak(state.answer) },
                        onAskAgain = { handleAskClick() }
                    )
                }

                is AskState.Error -> {
                    ErrorDisplayCard(
                        errorState = state,
                        onRetry = { handleAskClick() },
                        onDismiss = { viewModel.cancelOrReset() }
                    )
                }

                AskState.RequestingPermission -> {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                }

                is AskState.QuestionRecognized -> {
                    ProgressDisplayCard(askState = state)
                }
            }

            // Quick Manual Question Fallback (for testing / accessibility fallback)
            OutlinedButton(
                onClick = { showManualInput = !showManualInput },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("toggle_manual_input_button")
            ) {
                Text(if (showManualInput) "Hide Text Input Fallback" else "Type Question Directly (Testing Fallback)")
            }

            AnimatedVisibility(visible = showManualInput) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Manual Question Input",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Use this to test the 5-frame capture and AI pipeline without speaking.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = manualQuestionText,
                                onValueChange = { manualQuestionText = it },
                                label = { Text("e.g. What is in front of me?") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("manual_question_input"),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (manualQuestionText.isNotBlank()) {
                                        viewModel.askWithText(manualQuestionText)
                                    }
                                },
                                modifier = Modifier
                                    .height(56.dp)
                                    .testTag("manual_send_button")
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Send question")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Navigation Mode Button (Secondary main button as specified in prompt)
            Button(
                onClick = onNavigateToNavigation,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("navigation_mode_button")
                    .semantics { contentDescription = "Open Navigation Mode placeholder screen" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.NearMe, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Navigation Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun StatusOverviewCard(
    esp32Status: ConnectionStatus?,
    backendStatus: ConnectionStatus?,
    isTestingEsp32: Boolean,
    isTestingBackend: Boolean,
    isBluetooth: Boolean,
    audioRouteDesc: String,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "System Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh status",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ESP32 Status
            StatusRow(
                icon = Icons.Default.CameraAlt,
                label = "ESP32 Camera",
                statusText = when {
                    isTestingEsp32 -> "Checking..."
                    esp32Status?.isConnected == true -> "Connected"
                    else -> "Not Connected"
                },
                isSuccess = esp32Status?.isConnected == true,
                isLoading = isTestingEsp32
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Backend Status
            StatusRow(
                icon = Icons.Default.Cloud,
                label = "AI Backend",
                statusText = when {
                    isTestingBackend -> "Checking..."
                    backendStatus?.isConnected == true -> "Connected"
                    else -> "Not Connected"
                },
                isSuccess = backendStatus?.isConnected == true,
                isLoading = isTestingBackend
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Audio Route Status
            StatusRow(
                icon = Icons.Default.Headphones,
                label = "Audio Route",
                statusText = audioRouteDesc,
                isSuccess = isBluetooth,
                isLoading = false
            )
        }
    }
}

@Composable
fun StatusRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    statusText: String,
    isSuccess: Boolean,
    isLoading: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            Surface(
                color = if (isSuccess) StatusSuccess.copy(alpha = 0.15f) else StatusError.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = statusText,
                    color = if (isSuccess) StatusSuccess else StatusError,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun PrimaryAskButton(
    askState: AskState,
    onAskClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    val isActive = askState !is AskState.Idle &&
            askState !is AskState.Success &&
            askState !is AskState.Error

    val buttonLabel = when (askState) {
        is AskState.Idle -> "Ask SeeAssist"
        is AskState.RequestingPermission -> "Requesting Permission..."
        is AskState.Listening -> "Listening..."
        is AskState.QuestionRecognized -> "Question Heard..."
        is AskState.FlushingCamera -> "Flushing Camera Buffer..."
        is AskState.CapturingFrames -> "Capturing ${askState.currentFrame} of ${askState.totalFrames}..."
        is AskState.SelectingBestFrame -> "Selecting Best Frame..."
        is AskState.SendingToAI -> "Sending to AI..."
        is AskState.Analyzing -> "Analyzing..."
        is AskState.Speaking -> "Speaking Answer..."
        is AskState.Success -> "Ask Again"
        is AskState.Error -> "Try Again"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = {
                if (isActive) onCancelClick() else onAskClick()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .testTag("primary_ask_button")
                .semantics { contentDescription = buttonLabel },
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isActive) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                } else {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Text(
                    text = buttonLabel,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        }

        if (isActive) {
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
                onClick = onCancelClick,
                modifier = Modifier.testTag("cancel_operation_button")
            ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Cancel Operation")
            }
        }
    }
}

@Composable
fun ProgressDisplayCard(askState: AskState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(44.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(14.dp))

            when (askState) {
                is AskState.Listening -> {
                    Text(
                        text = "Listening to your voice...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Speak into your Bluetooth earbud or phone mic.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is AskState.QuestionRecognized -> {
                    Text(
                        text = "Question: \"${askState.question}\"",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                is AskState.FlushingCamera -> {
                    Text(
                        text = "Flushing stale camera frames...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Discarding 2 hardware buffer frames to ensure fresh capture.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is AskState.CapturingFrames -> {
                    Text(
                        text = "Capturing ${askState.currentFrame} of ${askState.totalFrames}...",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Acquiring fresh frames from ESP32-CAM via Wi-Fi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is AskState.SelectingBestFrame -> {
                    Text(
                        text = "Evaluating Image Quality...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Calculating Laplacian sharpness and exposure scores",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is AskState.SendingToAI,
                is AskState.Analyzing -> {
                    Text(
                        text = "Analyzing with SeeAssist AI...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Backend router selecting VQA, OCR, or Object Detection",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is AskState.Speaking -> {
                    Text(
                        text = "Speaking Answer...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                else -> {}
            }
        }
    }
}

@Composable
fun ResultDisplayCard(
    successState: AskState.Success,
    onReplaySpeech: () -> Unit,
    onAskAgain: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("result_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header with AI badge and Speak button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "AI: ${successState.selectedAi}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                IconButton(
                    onClick = onReplaySpeech,
                    modifier = Modifier.testTag("replay_speech_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Replay spoken answer",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Recognized Question
            Text(
                text = "Recognized Question:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "\"${successState.question}\"",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // SeeAssist Answer
            Text(
                text = "SeeAssist Answer:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = successState.answer,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Selected Best Frame Preview & Quality breakdown
            Text(
                text = "Selected Best Frame (Frame #${successState.selectedIndex} of 5):",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    bitmap = successState.bestFrameBitmap.asImageBitmap(),
                    contentDescription = "Selected best frame from ESP32 camera",
                    modifier = Modifier
                        .size(110.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    val currentScore = successState.frameScores.firstOrNull { it.frameIndex == successState.selectedIndex }
                    if (currentScore != null) {
                        Text(
                            text = "Quality Score: %.1f".format(currentScore.finalScore),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Sharpness: %.1f".format(currentScore.sharpness),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Brightness: %.0f / 255".format(currentScore.brightness),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Exposure: %.2f".format(currentScore.exposureScore),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Frame quality verified",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorDisplayCard(
    errorState: AskState.Error,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("error_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Notice (${errorState.stage})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = errorState.message,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("dismiss_error_button")
                ) {
                    Text("Dismiss")
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (errorState.canRetry) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.testTag("retry_button")
                    ) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

@Composable
fun HandsFreeControlCard(
    handsFreeState: HandsFreeState,
    handsFreePhase: HandsFreePhase = HandsFreePhase.LISTENING_FOR_WAKE_WORD,
    errorMessage: String?,
    wakeWordState: WakeWordState = WakeWordState.NONE,
    wakeWordPhrase: String? = null,
    wakeMicStatus: WakeMicStatus = WakeMicStatus(),
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hands_free_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Hearing,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Hands-Free Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Status Badge: OFF, STARTING, LISTENING PHASES, ERROR, or WAKE WORD DETECTED
                val (badgeText, badgeBg, badgeTextColor) = when {
                    wakeWordState == WakeWordState.DETECTED -> Triple(
                        "WAKE WORD DETECTED",
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.onPrimary
                    )
                    handsFreeState == HandsFreeState.OFF -> Triple(
                        "OFF",
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    handsFreeState == HandsFreeState.STARTING -> Triple(
                        "STARTING...",
                        StatusWarning.copy(alpha = 0.15f),
                        StatusWarning
                    )
                    handsFreeState == HandsFreeState.ERROR -> Triple(
                        "ERROR",
                        StatusError.copy(alpha = 0.15f),
                        StatusError
                    )
                    handsFreeState == HandsFreeState.LISTENING -> when (handsFreePhase) {
                        HandsFreePhase.LISTENING_FOR_WAKE_WORD -> Triple(
                            "LISTENING",
                            StatusSuccess.copy(alpha = 0.15f),
                            StatusSuccess
                        )
                        HandsFreePhase.LISTENING_FOR_COMMAND -> Triple(
                            "LISTENING FOR COMMAND...",
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        HandsFreePhase.PROCESSING -> Triple(
                            "PROCESSING...",
                            StatusWarning.copy(alpha = 0.15f),
                            StatusWarning
                        )
                        HandsFreePhase.SPEAKING -> Triple(
                            "SPEAKING...",
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    else -> Triple("OFF", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeTextColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Wake word transient detection visual feedback
            AnimatedVisibility(visible = wakeWordState == WakeWordState.DETECTED) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = wakeWordPhrase ?: "SeeAssist heard you",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Actual Wake Microphone Route Indicator (Bluetooth vs Phone mic fallback)
            Surface(
                color = if (wakeMicStatus.isBluetooth) StatusSuccess.copy(alpha = 0.12f)
                        else if (handsFreeState == HandsFreeState.LISTENING) StatusWarning.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (wakeMicStatus.isBluetooth) Icons.Default.Headphones else Icons.Default.Mic,
                        contentDescription = null,
                        tint = if (wakeMicStatus.isBluetooth) StatusSuccess
                               else if (handsFreeState == HandsFreeState.LISTENING) StatusWarning
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Wake Mic: ${wakeMicStatus.description}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = if (wakeMicStatus.isBluetooth) StatusSuccess
                                else if (handsFreeState == HandsFreeState.LISTENING) StatusWarning
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val explanation = when {
                wakeWordState == WakeWordState.DETECTED -> "Wake word detected: HEY SEE ASSIST! Prompting user..."
                handsFreeState == HandsFreeState.OFF -> "Start background microphone service. Once active, the phone screen can be turned off while SeeAssist listens for \"Hey SeeAssist\"."
                handsFreeState == HandsFreeState.STARTING -> "Starting microphone foreground service and initializing Sherpa-ONNX model..."
                handsFreeState == HandsFreeState.ERROR -> errorMessage ?: "An error occurred starting the hands-free audio service."
                handsFreeState == HandsFreeState.LISTENING -> when (handsFreePhase) {
                    HandsFreePhase.LISTENING_FOR_WAKE_WORD -> "Listening for \"Hey SeeAssist\" on-device. Phone screen may be turned off."
                    HandsFreePhase.LISTENING_FOR_COMMAND -> "Listening to your spoken question (e.g. 'What is in front of me?')..."
                    HandsFreePhase.PROCESSING -> "Capturing camera frames, selecting best image, and querying AI backend..."
                    HandsFreePhase.SPEAKING -> "Speaking answer aloud. Microphone will automatically resume listening afterward."
                }
                else -> ""
            }

            Text(
                text = explanation,
                style = MaterialTheme.typography.bodySmall,
                color = if (handsFreeState == HandsFreeState.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            when (handsFreeState) {
                HandsFreeState.OFF, HandsFreeState.ERROR -> {
                    Button(
                        onClick = onStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("start_hands_free_button")
                            .semantics { contentDescription = "Start Hands-Free background listening service" },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Start Hands-Free",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                HandsFreeState.STARTING, HandsFreeState.LISTENING -> {
                    Button(
                        onClick = onStop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("stop_hands_free_button")
                            .semantics { contentDescription = "Stop Hands-Free background listening service" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Stop Hands-Free",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
