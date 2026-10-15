package com.example.network

import android.util.Log
import com.example.model.BackendResult
import com.example.model.ConnectionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Parameter names discovered from Gradio /gradio_api/info or defaults.
 */
data class SeeAssistApiParameters(
    val imageParamName: String = "image_path",
    val questionParamName: String = "question"
)

/**
 * Communicates with the SeeAssist AI backend on Google Colab Gradio.
 *
 * Implements the Gradio v2 API specification:
 * 1. Fetches <BACKEND_URL>/gradio_api/info to discover parameter names for "seeassist_api"
 * 2. Uploads selected best-frame JPEG via POST <BACKEND_URL>/gradio_api/upload (multipart field: "files")
 * 3. Creates FileData object: {"path": "<server_path>", "orig_name": "best_frame.jpg", "meta": {"_type": "gradio.FileData"}}
 * 4. Calls POST <BACKEND_URL>/gradio_api/call/v2/seeassist_api with named parameters (e.g. {"image_path": FileData, "question": "..."})
 *    (Never generic "data" or "key" wrappers)
 * 5. Retrieves event_id and streams result from GET <BACKEND_URL>/gradio_api/call/seeassist_api/<EVENT_ID>
 * 6. Returns Output 1 (Selected AI) and Output 2 (Answer)
 */
class SeeAssistBackendClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val jpegMediaType = "image/jpeg".toMediaType()

    companion object {
        private const val TAG = "SeeAssistBackend"
        const val FUNCTION_NAME = "seeassist_api"
    }

    /**
     * Inspects <BACKEND_URL>/gradio_api/info to find exact parameter names for seeassist_api.
     * Default fallback corresponds to def seeassist_api(image_path, question):
     * imageParamName = "image_path", questionParamName = "question".
     */
    fun fetchApiParameters(baseUrl: String): SeeAssistApiParameters {
        val sanitized = baseUrl.trim().removeSuffix("/")
        val infoUrl = "$sanitized/gradio_api/info"

        try {
            val request = Request.Builder()
                .url(infoUrl)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                Log.d(TAG, "INFO: GET $infoUrl -> HTTP ${response.code} (length: ${body?.length ?: 0})")

                if (response.isSuccessful && !body.isNullOrBlank()) {
                    return parseApiParametersFromJson(body)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch /gradio_api/info from $infoUrl (${e.message}), using default parameter names.")
        }

        return SeeAssistApiParameters(imageParamName = "image_path", questionParamName = "question")
    }

    /**
     * Parses the Gradio /gradio_api/info response JSON for the seeassist_api endpoint parameters.
     */
    fun parseApiParametersFromJson(infoJson: String): SeeAssistApiParameters {
        try {
            val json = JSONObject(infoJson)
            val namedEndpoints = json.optJSONObject("named_endpoints") ?: json.optJSONObject("endpoints")
            if (namedEndpoints != null) {
                // Check both "/seeassist_api" and "seeassist_api"
                val endpointObj = namedEndpoints.optJSONObject("/$FUNCTION_NAME")
                    ?: namedEndpoints.optJSONObject(FUNCTION_NAME)

                if (endpointObj != null) {
                    val paramsArray = endpointObj.optJSONArray("parameters")
                    if (paramsArray != null && paramsArray.length() >= 2) {
                        val p0 = paramsArray.getJSONObject(0)
                        val p1 = paramsArray.getJSONObject(1)

                        val p0Name = p0.optString("parameter_name").takeIf { it.isNotBlank() }
                            ?: p0.optString("name").takeIf { it.isNotBlank() }
                            ?: p0.optString("label").takeIf { it.isNotBlank() }
                            ?: "image_path"

                        val p1Name = p1.optString("parameter_name").takeIf { it.isNotBlank() }
                            ?: p1.optString("name").takeIf { it.isNotBlank() }
                            ?: p1.optString("label").takeIf { it.isNotBlank() }
                            ?: "question"

                        Log.i(TAG, "Discovered parameter names from /gradio_api/info -> [$p0Name, $p1Name]")
                        return SeeAssistApiParameters(imageParamName = p0Name, questionParamName = p1Name)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing /gradio_api/info JSON: ${e.message}")
        }

        return SeeAssistApiParameters(imageParamName = "image_path", questionParamName = "question")
    }

    /**
     * Tests connectivity to the Gradio backend.
     * Connected indicates reachability of the Gradio server.
     */
    suspend fun testConnection(baseUrl: String): ConnectionStatus = withContext(Dispatchers.IO) {
        val sanitized = baseUrl.trim().removeSuffix("/")
        if (sanitized.isBlank()) {
            return@withContext ConnectionStatus(
                isConnected = false,
                message = "Backend URL is empty. Please enter a valid URL in Settings."
            )
        }

        val testEndpoints = listOf(
            "$sanitized/gradio_api/info",
            "$sanitized/gradio_api/openapi.json",
            sanitized
        )

        for (endpoint in testEndpoints) {
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful || response.code in 200..399) {
                        return@withContext ConnectionStatus(
                            isConnected = true,
                            message = "AI Backend Connected (${response.code} OK)"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Ping to $endpoint failed: ${e.message}")
            }
        }

        ConnectionStatus(
            isConnected = false,
            message = "Unable to connect to SeeAssist backend at $sanitized. Please verify the URL in Settings."
        )
    }

    /**
     * Executes the complete Gradio workflow:
     * STEP 1: Upload image via POST /gradio_api/upload
     * STEP 2: Create Gradio FileData object
     * STEP 3: Call POST /gradio_api/call/v2/seeassist_api with named parameters
     * STEP 4: Stream result from GET /gradio_api/call/seeassist_api/<EVENT_ID>
     * STEP 5: Extract selected AI and answer
     */
    suspend fun querySeeAssist(
        baseUrl: String,
        imageBytes: ByteArray,
        question: String
    ): Result<BackendResult> = withContext(Dispatchers.IO) {
        val sanitizedUrl = baseUrl.trim().removeSuffix("/")
        if (sanitizedUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Backend URL is not configured in Settings."))
        }

        // STEP 0: Discover exact parameter names from /gradio_api/info
        val paramsInfo = fetchApiParameters(sanitizedUrl)

        // STEP 1: Upload JPEG image to POST /gradio_api/upload
        val uploadUrl = "$sanitizedUrl/gradio_api/upload"
        val serverImagePathResult = uploadImageToGradio(uploadUrl, imageBytes)
        if (serverImagePathResult.isFailure) {
            val ex = serverImagePathResult.exceptionOrNull()
            return@withContext Result.failure(
                IOException("Image upload failed: ${ex?.localizedMessage}", ex)
            )
        }
        val serverImagePath = serverImagePathResult.getOrThrow()

        // STEP 2: Create FileData object
        val fileDataObject = JSONObject().apply {
            put("path", serverImagePath)
            put("orig_name", "best_frame.jpg")
            put("meta", JSONObject().apply {
                put("_type", "gradio.FileData")
            })
        }

        // STEP 3: Call POST /gradio_api/call/v2/seeassist_api with exact parameter names
        val callPayload = JSONObject().apply {
            put(paramsInfo.imageParamName, fileDataObject)
            put(paramsInfo.questionParamName, question)
        }

        val primaryCallUrl = "$sanitizedUrl/gradio_api/call/v2/$FUNCTION_NAME"
        val fallbackCallUrl = "$sanitizedUrl/gradio_api/call/$FUNCTION_NAME"

        val callResult = executeCallApi(primaryCallUrl, fallbackCallUrl, callPayload, paramsInfo)
        if (callResult.isFailure) {
            return@withContext Result.failure(callResult.exceptionOrNull()!!)
        }
        val eventId = callResult.getOrThrow()

        // STEP 4: Stream result from GET /gradio_api/call/seeassist_api/<EVENT_ID>
        val resultUrl = "$sanitizedUrl/gradio_api/call/$FUNCTION_NAME/$eventId"
        val fallbackResultUrl = "$sanitizedUrl/gradio_api/call/v2/$FUNCTION_NAME/$eventId"

        return@withContext streamResultFromGradio(resultUrl, fallbackResultUrl, eventId)
    }

    /**
     * STEP 1: Uploads the JPEG image using multipart/form-data to /gradio_api/upload.
     * Field name: "files"
     */
    private fun uploadImageToGradio(uploadUrl: String, imageBytes: ByteArray): Result<String> {
        Log.i(TAG, "UPLOAD: Request URL: $uploadUrl | Image size: ${imageBytes.size} bytes")

        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "files",
                    "best_frame.jpg",
                    imageBytes.toRequestBody(jpegMediaType)
                )
                .build()

            val request = Request.Builder()
                .url(uploadUrl)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                Log.i(TAG, "UPLOAD: HTTP Status: ${response.code} | Response: $body")

                if (!response.isSuccessful) {
                    return Result.failure(
                        IOException("POST $uploadUrl failed with HTTP ${response.code}: $body")
                    )
                }

                if (body.isBlank()) {
                    return Result.failure(IOException("Empty upload response from $uploadUrl"))
                }

                val jsonArray = JSONArray(body)
                if (jsonArray.length() == 0) {
                    return Result.failure(IOException("Upload response contained 0 file paths"))
                }

                val firstItem = jsonArray.get(0)
                val serverPath = if (firstItem is JSONObject) {
                    firstItem.optString("path").takeIf { it.isNotBlank() }
                        ?: return Result.failure(IOException("Upload response JSON object missing 'path': $body"))
                } else {
                    firstItem.toString()
                }

                Log.i(TAG, "UPLOAD: Extracted server-side file path: $serverPath")
                return Result.success(serverPath)
            }
        } catch (e: Exception) {
            Log.e(TAG, "UPLOAD: Error uploading file to $uploadUrl: ${e.message}", e)
            return Result.failure(e)
        }
    }

    /**
     * STEP 3: Submits the request using the registered keyword parameter names.
     */
    private fun executeCallApi(
        primaryUrl: String,
        fallbackUrl: String,
        payload: JSONObject,
        paramsInfo: SeeAssistApiParameters
    ): Result<String> {
        val payloadString = payload.toString()
        val paramKeys = listOf(paramsInfo.imageParamName, paramsInfo.questionParamName)
        Log.i(TAG, "CALL: Exact JSON parameter names sent: $paramKeys")

        // Try primary URL (/call/v2/seeassist_api) first, then fallback (/call/seeassist_api)
        val urlsToTry = listOf(primaryUrl, fallbackUrl)
        var lastError: Exception? = null

        for (url in urlsToTry) {
            try {
                Log.i(TAG, "CALL: Attempting POST $url")

                val postRequest = Request.Builder()
                    .url(url)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .post(payloadString.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(postRequest).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    Log.i(TAG, "CALL: POST $url -> HTTP Status: ${response.code} | Response: $body")

                    if (response.code == 404 && url != urlsToTry.last()) {
                        Log.w(TAG, "POST $url returned 404, trying next endpoint...")
                        return@use
                    }

                    if (!response.isSuccessful) {
                        lastError = IOException("POST $url failed with HTTP ${response.code}: $body")
                        return@use
                    }

                    val json = JSONObject(body)
                    val eventId = json.optString("event_id")
                    if (eventId.isNotBlank()) {
                        Log.i(TAG, "CALL: Obtained event_id: $eventId")
                        return Result.success(eventId)
                    } else {
                        lastError = IOException("Response did not contain 'event_id': $body")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "CALL: Error calling $url: ${e.message}", e)
                lastError = e
            }
        }

        return Result.failure(lastError ?: IOException("Failed to invoke Gradio seeassist_api endpoint."))
    }

    /**
     * STEP 4: Streams the event result from GET /gradio_api/call/seeassist_api/<EVENT_ID>
     */
    private fun streamResultFromGradio(
        resultUrl: String,
        fallbackResultUrl: String,
        eventId: String
    ): Result<BackendResult> {
        val urlsToTry = listOf(resultUrl, fallbackResultUrl)
        var lastError: Exception? = null

        for (url in urlsToTry) {
            try {
                Log.i(TAG, "RESULT: Polling event_id: $eventId via GET $url")

                val eventRequest = Request.Builder()
                    .url(url)
                    .header("Accept", "text/event-stream")
                    .get()
                    .build()

                client.newCall(eventRequest).execute().use { response ->
                    if (response.code == 404 && url != urlsToTry.last()) {
                        Log.w(TAG, "GET $url returned 404, trying fallback result URL...")
                        return@use
                    }

                    if (!response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        Log.e(TAG, "RESULT: GET $url failed -> HTTP Status: ${response.code} | Response: $body")
                        lastError = IOException("GET $url failed with HTTP ${response.code}: $body")
                        return@use
                    }

                    val stream = response.body?.byteStream()
                        ?: return Result.failure(IOException("Empty byte stream received from $url"))

                    val reader = BufferedReader(InputStreamReader(stream))
                    var line: String?
                    var currentEvent: String? = null
                    var lastData: String? = null

                    while (reader.readLine().also { line = it } != null) {
                        val trimmed = line?.trim() ?: continue
                        if (trimmed.startsWith("event:")) {
                            currentEvent = trimmed.removePrefix("event:").trim()
                        } else if (trimmed.startsWith("data:")) {
                            lastData = trimmed.removePrefix("data:").trim()
                            if (currentEvent == "complete") {
                                break
                            }
                        }
                    }

                    Log.i(TAG, "RESULT: Completed event: '$currentEvent' | Result Data: $lastData")

                    if (currentEvent == "error") {
                        return Result.failure(IOException("Gradio inference error: $lastData"))
                    }

                    if (!lastData.isNullOrBlank()) {
                        return parseBackendData(lastData)
                    }

                    lastError = IOException("Did not receive complete event data from Gradio stream: $url")
                }
            } catch (e: Exception) {
                Log.e(TAG, "RESULT: Error streaming from $url: ${e.message}", e)
                lastError = e
            }
        }

        return Result.failure(lastError ?: IOException("Failed to retrieve result for event $eventId"))
    }

    /**
     * Parses the Gradio output into BackendResult:
     * Output 1: selected AI (e.g. "VQA", "OCR", "OBJECT_DETECTION")
     * Output 2: answer text
     */
    fun parseBackendData(rawJson: String): Result<BackendResult> {
        try {
            // Case 1: JSON array -> ["VQA", "There is a chair in front of you."]
            if (rawJson.startsWith("[")) {
                val array = JSONArray(rawJson)
                val selectedAi = if (array.length() > 0) array.optString(0, "VQA") else "VQA"
                val answer = if (array.length() > 1) array.optString(1, "") else array.optString(0, "")
                return Result.success(BackendResult(selectedAi = selectedAi, answer = answer, rawResponse = rawJson))
            }

            // Case 2: Object with "data" array -> {"data": ["VQA", "Answer text"]}
            val obj = JSONObject(rawJson)
            if (obj.has("data")) {
                val data = obj.getJSONArray("data")
                val selectedAi = if (data.length() > 0) data.optString(0, "VQA") else "VQA"
                val answer = if (data.length() > 1) data.optString(1, "") else data.optString(0, "")
                return Result.success(BackendResult(selectedAi = selectedAi, answer = answer, rawResponse = rawJson))
            }

            // Case 3: Object with "output"
            if (obj.has("output")) {
                val output = obj.get("output")
                if (output is JSONArray) {
                    val selectedAi = if (output.length() > 0) output.optString(0, "VQA") else "VQA"
                    val answer = if (output.length() > 1) output.optString(1, "") else output.optString(0, "")
                    return Result.success(BackendResult(selectedAi = selectedAi, answer = answer, rawResponse = rawJson))
                }
                return Result.success(BackendResult(selectedAi = "SeeAssist", answer = output.toString(), rawResponse = rawJson))
            }

            return Result.failure(IOException("Unrecognized backend JSON format: ${rawJson.take(150)}"))
        } catch (e: Exception) {
            return Result.failure(IOException("Failed to parse backend response: ${e.localizedMessage}", e))
        }
    }
}
