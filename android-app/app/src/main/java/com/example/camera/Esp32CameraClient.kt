package com.example.camera

import android.graphics.BitmapFactory
import com.example.model.CapturedFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Handles all Wi-Fi HTTP communication with the AI-Thinker ESP32-CAM (OV2640).
 *
 * Designed specifically for ESP32 CameraWebServer compatibility:
 * - Every /capture request uses a fresh HTTP connection (ConnectionPool size 0)
 * - Explicit "Connection: close" request header to prevent socket reuse / keep-alive poisoning
 * - Full read of JPEG response bytes before closing response body
 * - Guaranteed clean closure of response and connection eviction
 * - Buffer flush: 2 discarded frames with ~300ms delays
 * - 5-frame capture: fresh connections with ~500ms delays between frames
 * - Single-frame automatic retry with fresh connection if an individual request fails
 */
class Esp32CameraClient {

    /**
     * Creates a fresh OkHttpClient instance with no connection pooling.
     * Prevents "Unexpected status line: 0" caused by ESP32 server closing sockets unexpectedly.
     */
    private fun createFreshHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectionPool(ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
            .protocols(listOf(Protocol.HTTP_1_1))
            .retryOnConnectionFailure(false)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Constructs the capture URL for a given ESP32 IP address.
     */
    fun buildCaptureUrl(esp32Ip: String): String {
        val sanitizedIp = esp32Ip.trim().removePrefix("http://").removePrefix("https://").removeSuffix("/")
        return "http://$sanitizedIp/capture"
    }

    /**
     * Tests whether the ESP32-CAM is reachable on the local Wi-Fi network and returns a valid image.
     */
    suspend fun testConnection(esp32Ip: String): Result<String> = withContext(Dispatchers.IO) {
        val url = buildCaptureUrl(esp32Ip)
        val client = createFreshHttpClient()
        try {
            val request = Request.Builder()
                .url(url)
                .header("Connection", "close")
                .header("Accept", "image/jpeg, */*")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("ESP32-CAM returned HTTP ${response.code}: ${response.message}")
                    )
                }

                val body = response.body ?: return@withContext Result.failure(
                    IOException("ESP32-CAM returned an empty response body.")
                )

                val bytes = body.bytes()
                if (bytes.isEmpty()) {
                    return@withContext Result.failure(
                        IOException("ESP32-CAM returned 0 bytes.")
                    )
                }

                // Verify valid JPEG header (0xFF, 0xD8)
                if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
                    Result.success("ESP32-CAM Connected (received ${bytes.size / 1024} KB JPEG)")
                } else {
                    Result.failure(IOException("Received response from ESP32, but it was not a valid JPEG image."))
                }
            }
        } catch (e: Exception) {
            Result.failure(
                IOException("Unable to connect to ESP32-CAM at $url: ${e.localizedMessage}. Check Wi-Fi connection and IP address.", e)
            )
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    /**
     * Captures a single image from the ESP32-CAM using a fresh HTTP connection.
     * Includes "Connection: close" and cleanly reads the entire byte stream before closing.
     */
    suspend fun captureSingleFrame(esp32Ip: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        val url = buildCaptureUrl(esp32Ip)
        val client = createFreshHttpClient()
        try {
            val request = Request.Builder()
                .url(url)
                .header("Connection", "close")
                .header("Accept", "image/jpeg, */*")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("ESP32 HTTP ${response.code} error: ${response.message}")
                    )
                }

                val body = response.body ?: return@withContext Result.failure(
                    IOException("Empty image response from ESP32")
                )

                val bytes = body.bytes()
                if (bytes.isEmpty()) {
                    return@withContext Result.failure(IOException("Empty byte array received from ESP32"))
                }

                Result.success(bytes)
            }
        } catch (e: Exception) {
            Result.failure(IOException("Failed to capture image from ESP32 at $url: ${e.localizedMessage}", e))
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    /**
     * Captures a single frame, retrying once with a fresh connection if the first attempt fails.
     */
    suspend fun captureSingleFrameWithRetry(esp32Ip: String): Result<ByteArray> {
        val firstAttempt = captureSingleFrame(esp32Ip)
        if (firstAttempt.isSuccess) {
            return firstAttempt
        }

        // Brief delay before retrying with a brand new HTTP connection
        delay(150)
        return captureSingleFrame(esp32Ip)
    }

    /**
     * Flushes stale buffered frames from the ESP32-CAM hardware buffer.
     *
     * Flow:
     * 1. Request one frame from /capture using fresh connection and discard it.
     * 2. Wait ~300 ms.
     * 3. Request second frame using fresh connection and discard it.
     * 4. Wait ~300 ms.
     */
    suspend fun flushStaleFrames(esp32Ip: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // First flush frame
            val flush1 = captureSingleFrameWithRetry(esp32Ip)
            if (flush1.isFailure) {
                return@withContext Result.failure(flush1.exceptionOrNull() ?: IOException("Buffer flush frame 1 failed"))
            }
            delay(300)

            // Second flush frame
            val flush2 = captureSingleFrameWithRetry(esp32Ip)
            if (flush2.isFailure) {
                return@withContext Result.failure(flush2.exceptionOrNull() ?: IOException("Buffer flush frame 2 failed"))
            }
            delay(300)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(IOException("Failed during ESP32 buffer flush: ${e.localizedMessage}", e))
        }
    }

    /**
     * Captures five fresh JPEG frames from the ESP32-CAM after flushing the buffer.
     *
     * @param esp32Ip IP address of the ESP32
     * @param onProgress Callback invoked before each capture with (currentFrameIndex, totalFrames)
     * @return List of 5 CapturedFrame objects containing bytes and decoded Bitmaps
     */
    suspend fun captureFiveFrames(
        esp32Ip: String,
        onProgress: suspend (current: Int, total: Int) -> Unit
    ): Result<List<CapturedFrame>> = withContext(Dispatchers.IO) {
        val frames = mutableListOf<CapturedFrame>()
        val total = 5

        for (i in 1..total) {
            onProgress(i, total)

            // Capture using fresh connection with single-frame retry
            val captureResult = captureSingleFrameWithRetry(esp32Ip)
            if (captureResult.isFailure) {
                val error = captureResult.exceptionOrNull()
                return@withContext Result.failure(
                    IOException("Failed while capturing frame $i of $total: ${error?.localizedMessage}", error)
                )
            }

            val bytes = captureResult.getOrThrow()
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@withContext Result.failure(
                    IOException("Failed to decode JPEG bytes into Bitmap for frame $i")
                )

            frames.add(CapturedFrame(index = i, jpegBytes = bytes, bitmap = bitmap))

            // Wait approximately 500 ms between each of the five real captured frames
            if (i < total) {
                delay(500)
            }
        }

        Result.success(frames)
    }
}
