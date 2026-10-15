package com.example.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Bluetooth audio routing for headset/earbud voice input and output.
 *
 * Requirements:
 * - Inspect available communication devices on modern Android (API 31+)
 * - Prefer BLE headset or Bluetooth SCO headset when available
 * - Use setCommunicationDevice() when supported
 * - Fall back gracefully to phone microphone and speaker if Bluetooth is unavailable
 * - Never crash if no Bluetooth device is present
 */
class BluetoothAudioManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _isBluetoothConnected = MutableStateFlow(false)
    val isBluetoothConnected: StateFlow<Boolean> = _isBluetoothConnected.asStateFlow()

    private val _audioRouteDescription = MutableStateFlow("Phone Audio")
    val audioRouteDescription: StateFlow<String> = _audioRouteDescription.asStateFlow()

    init {
        updateAudioRouting()
    }

    /**
     * Checks available audio devices and sets preferred communication device to Bluetooth if present.
     */
    fun updateAudioRouting(): Boolean {
        if (audioManager == null) {
            _isBluetoothConnected.value = false
            _audioRouteDescription.value = "Phone Audio"
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val devices = audioManager.availableCommunicationDevices
                val bluetoothDevice = devices.firstOrNull { device ->
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }

                if (bluetoothDevice != null) {
                    val success = audioManager.setCommunicationDevice(bluetoothDevice)
                    Log.i("BluetoothAudioManager", "Bluetooth communication route requested: ${bluetoothDevice.productName} (type: ${bluetoothDevice.type}), setCommunicationDevice result: $success")
                    if (success) {
                        _isBluetoothConnected.value = true
                        _audioRouteDescription.value = "Bluetooth Earbud (${bluetoothDevice.productName})"
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.w("BluetoothAudioManager", "Error configuring Bluetooth communication device: ${e.message}")
            }
        } else {
            @Suppress("DEPRECATION")
            try {
                if (audioManager.isBluetoothScoAvailableOffCall) {
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                    _isBluetoothConnected.value = true
                    _audioRouteDescription.value = "Bluetooth Headset"
                    return true
                }
            } catch (e: Exception) {
                Log.w("BluetoothAudioManager", "Error starting Bluetooth SCO: ${e.message}")
            }
        }

        _isBluetoothConnected.value = false
        _audioRouteDescription.value = "Phone Audio"
        return false
    }

    /**
     * Releases Bluetooth communication device routing.
     */
    fun releaseRouting() {
        if (audioManager == null) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                audioManager.clearCommunicationDevice()
            } catch (e: Exception) {
                Log.w("BluetoothAudioManager", "Error clearing communication device: ${e.message}")
            }
        } else {
            @Suppress("DEPRECATION")
            try {
                if (audioManager.isBluetoothScoOn) {
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                }
            } catch (e: Exception) {
                Log.w("BluetoothAudioManager", "Error stopping Bluetooth SCO: ${e.message}")
            }
        }
    }
}
