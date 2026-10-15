package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.camera.Esp32CameraClient
import com.example.camera.FrameQualitySelector
import com.example.model.CapturedFrame
import com.example.network.SeeAssistBackendClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SeeAssist", appName)
    }

    @Test
    fun `esp32 client builds valid capture url`() {
        val client = Esp32CameraClient()
        val url = client.buildCaptureUrl("192.168.0.119")
        assertEquals("http://192.168.0.119/capture", url)

        val urlWithProtocol = client.buildCaptureUrl("http://192.168.0.150/")
        assertEquals("http://192.168.0.150/capture", urlWithProtocol)
    }

    @Test
    fun `gradio parameter names parsed from info json`() {
        val client = SeeAssistBackendClient()
        val sampleInfo = """
            {
              "named_endpoints": {
                "/seeassist_api": {
                  "parameters": [
                    {"parameter_name": "image_path", "type": "string"},
                    {"parameter_name": "question", "type": "string"}
                  ]
                }
              }
            }
        """.trimIndent()
        val params = client.parseApiParametersFromJson(sampleInfo)
        assertEquals("image_path", params.imageParamName)
        assertEquals("question", params.questionParamName)
    }

    @Test
    fun `gradio response parsing handles array format`() {
        val client = SeeAssistBackendClient()
        val json = """["VQA", "There is a water bottle in front of you."]"""
        val result = client.parseBackendData(json)
        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals("VQA", data.selectedAi)
        assertEquals("There is a water bottle in front of you.", data.answer)
    }

    @Test
    fun `gradio response parsing handles object with data array`() {
        val client = SeeAssistBackendClient()
        val json = """{"data": ["OCR", "The text says 'EXIT'."]}"""
        val result = client.parseBackendData(json)
        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals("OCR", data.selectedAi)
        assertEquals("The text says 'EXIT'.", data.answer)
    }

    @Test
    fun `frame quality selector selects sharpest frame`() = runBlocking {
        val selector = FrameQualitySelector()

        val blurryBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.GRAY)
        }

        val sharpBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until 100) {
                for (x in 0 until 100) {
                    val color = if ((x / 10 + y / 10) % 2 == 0) Color.WHITE else Color.BLACK
                    setPixel(x, y, color)
                }
            }
        }

        val frames = listOf(
            CapturedFrame(1, ByteArray(10), blurryBitmap),
            CapturedFrame(2, ByteArray(10), sharpBitmap)
        )

        val result = selector.selectBestFrame(frames)
        assertTrue(result.isSuccess)
        val selection = result.getOrThrow()
        assertEquals(2, selection.frame.index)
        assertTrue(selection.score.finalScore > 0f)
    }

    @Test
    fun `hands free service targets 16kHz mono 16-bit PCM`() {
        assertEquals(16000, com.example.service.HandsFreeService.SAMPLE_RATE_HZ)
        assertEquals(android.media.AudioFormat.CHANNEL_IN_MONO, com.example.service.HandsFreeService.CHANNEL_CONFIG)
        assertEquals(android.media.AudioFormat.ENCODING_PCM_16BIT, com.example.service.HandsFreeService.AUDIO_FORMAT)
    }

    @Test
    fun `hands free states cover required lifecycle`() {
        val states = com.example.model.HandsFreeState.values().toList()
        assertTrue(states.contains(com.example.model.HandsFreeState.OFF))
        assertTrue(states.contains(com.example.model.HandsFreeState.STARTING))
        assertTrue(states.contains(com.example.model.HandsFreeState.LISTENING))
        assertTrue(states.contains(com.example.model.HandsFreeState.ERROR))
    }

    @Test
    fun `wake word state covers required events`() {
        val states = com.example.model.WakeWordState.values().toList()
        assertTrue(states.contains(com.example.model.WakeWordState.NONE))
        assertTrue(states.contains(com.example.model.WakeWordState.DETECTED))
    }

    @Test
    fun `hands free phase covers required phases`() {
        val phases = com.example.model.HandsFreePhase.values().toList()
        assertTrue(phases.contains(com.example.model.HandsFreePhase.LISTENING_FOR_WAKE_WORD))
        assertTrue(phases.contains(com.example.model.HandsFreePhase.LISTENING_FOR_COMMAND))
        assertTrue(phases.contains(com.example.model.HandsFreePhase.PROCESSING))
        assertTrue(phases.contains(com.example.model.HandsFreePhase.SPEAKING))
    }

    @Test
    fun `wake mic status covers Bluetooth and phone fallback`() {
        val btStatus = com.example.model.WakeMicStatus(
            routeType = com.example.model.WakeMicRouteType.BLUETOOTH,
            deviceName = "realme Buds T110",
            deviceType = android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            isBluetooth = true,
            description = "realme Buds T110 (Bluetooth)"
        )
        assertTrue(btStatus.isBluetooth)
        assertEquals("realme Buds T110 (Bluetooth)", btStatus.description)

        val fallbackStatus = com.example.model.WakeMicStatus(
            routeType = com.example.model.WakeMicRouteType.PHONE_MIC,
            deviceName = "Built-in Microphone",
            deviceType = android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC,
            isBluetooth = false,
            description = "Phone microphone (fallback)"
        )
        assertFalse(fallbackStatus.isBluetooth)
        assertEquals("Phone microphone (fallback)", fallbackStatus.description)
    }

    @Test
    fun `wake word detector is configured for HEY SEE ASSIST at 16kHz`() {
        assertEquals("HEY SEE ASSIST", com.example.wakeword.SherpaWakeWordDetector.WAKE_PHRASE)
        assertEquals(16000, com.example.wakeword.SherpaWakeWordDetector.SAMPLE_RATE_HZ)
        assertEquals("sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01", com.example.wakeword.SherpaWakeWordDetector.MODEL_DIR)
    }

    @Test
    fun `sherpa model assets are present in assets folder`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val assets = context.assets

        val requiredFiles = listOf(
            "sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01/encoder-epoch-12-avg-2-chunk-16-left-64.onnx",
            "sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01/decoder-epoch-12-avg-2-chunk-16-left-64.onnx",
            "sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01/joiner-epoch-12-avg-2-chunk-16-left-64.onnx",
            "sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01/tokens.txt",
            "sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01/keywords.txt"
        )

        for (path in requiredFiles) {
            val stream = assets.open(path)
            assertNotNull(stream)
            stream.close()
        }

        val keywordsContent = assets.open("sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01/keywords.txt")
            .bufferedReader().use { it.readText().trim() }
        assertEquals("▁HE Y ▁SEE ▁AS S IST", keywordsContent)

        val rawKeywordsContent = assets.open("sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01/keywords_raw.txt")
            .bufferedReader().use { it.readText().trim() }
        assertEquals("HEY SEE ASSIST", rawKeywordsContent)
    }
}
