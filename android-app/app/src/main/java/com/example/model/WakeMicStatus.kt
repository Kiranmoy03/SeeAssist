package com.example.model

enum class WakeMicRouteType {
    BLUETOOTH,
    PHONE_MIC,
    UNAVAILABLE
}

/**
 * Encapsulates the genuine active microphone routing for Hands-Free wake-word listening.
 *
 * Sourced directly from AudioRecord.routedDevice and the active communication device,
 * rather than simple Bluetooth connection state.
 */
data class WakeMicStatus(
    val routeType: WakeMicRouteType = WakeMicRouteType.UNAVAILABLE,
    val deviceName: String = "None",
    val deviceType: Int = 0,
    val isBluetooth: Boolean = false,
    val description: String = "Inactive (Service OFF)"
)
