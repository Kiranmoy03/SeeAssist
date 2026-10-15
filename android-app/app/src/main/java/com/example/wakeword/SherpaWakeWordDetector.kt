package com.example.wakeword

import android.content.res.AssetManager
import android.os.Build
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.FileNotFoundException

/**
 * Manages local, offline keyword spotting using official sherpa-onnx Zipformer GigaSpeech KWS model.
 *
 * Configured for wake phrase: "HEY SEE ASSIST" (BPE: "▁HE Y ▁SEE ▁AS S IST")
 *
 * Runs completely on-device using CPU inference.
 */
class SherpaWakeWordDetector {

    companion object {
        private const val TAG = "SherpaWakeWord"
        const val MODEL_DIR = "sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01"
        const val WAKE_PHRASE = "HEY SEE ASSIST"
        const val SAMPLE_RATE_HZ = 16000
    }

    private var spotter: KeywordSpotter? = null
    private var stream: OnlineStream? = null

    /**
     * Initializes the KeywordSpotter once per hands-free session using AssetManager.
     * All steps are logged and protected against fatal crashes.
     */
    fun initialize(assetManager: AssetManager): Result<Unit> {
        Log.i(TAG, "KWS STEP 1: Starting sherpa initialization")
        Log.i(TAG, "Device Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}")

        // Step 1: Verify all required assets before touching any native code
        val requiredAssets = listOf(
            "$MODEL_DIR/encoder-epoch-12-avg-2-chunk-16-left-64.onnx",
            "$MODEL_DIR/decoder-epoch-12-avg-2-chunk-16-left-64.onnx",
            "$MODEL_DIR/joiner-epoch-12-avg-2-chunk-16-left-64.onnx",
            "$MODEL_DIR/tokens.txt",
            "$MODEL_DIR/keywords.txt"
        )

        var missingAsset: String? = null
        for (path in requiredAssets) {
            try {
                assetManager.open(path).use { inStream ->
                    if (path.endsWith("keywords.txt")) {
                        val content = inStream.bufferedReader().readText()
                        Log.i(TAG, "keywords.txt content: ${content.trim()}")
                    }
                }
                Log.i(TAG, "KWS asset OK: $path")
            } catch (e: Exception) {
                Log.e(TAG, "KWS asset MISSING: $path (${e.message})")
                if (missingAsset == null) {
                    missingAsset = path
                }
            }
        }

        if (missingAsset != null) {
            val err = "Missing required KWS asset: $missingAsset"
            Log.e(TAG, err)
            return Result.failure(FileNotFoundException(err))
        }
        Log.i(TAG, "KWS STEP 2: Assets verified")

        // Step 2: Verify native library loading safely
        Log.i(TAG, "KWS STEP 3: Loading native library")
        try {
            try {
                System.loadLibrary("onnxruntime")
                Log.d(TAG, "Preloaded libonnxruntime.so successfully")
            } catch (t: Throwable) {
                Log.d(TAG, "Note: libonnxruntime.so load note: ${t.message}")
            }
            try {
                System.loadLibrary("sherpa-onnx-c-api")
                Log.d(TAG, "Preloaded libsherpa-onnx-c-api.so successfully")
            } catch (t: Throwable) {
                Log.d(TAG, "Note: libsherpa-onnx-c-api.so load note: ${t.message}")
            }
            System.loadLibrary("sherpa-onnx-jni")
            Log.i(TAG, "KWS STEP 4: Native library loaded")
        } catch (e: UnsatisfiedLinkError) {
            val err = "Failed to load sherpa-onnx native library: ${e.message}"
            Log.e(TAG, err, e)
            return Result.failure(e)
        } catch (t: Throwable) {
            val err = "Failed to load sherpa-onnx native library: ${t.message}"
            Log.e(TAG, err, t)
            return Result.failure(t)
        }

        // Step 3: Build configuration matching official GigaSpeech Zipformer2 KWS example
        try {
            Log.i(TAG, "KWS STEP 5: Building config")
            val transducerConfig = OnlineTransducerModelConfig(
                encoder = "$MODEL_DIR/encoder-epoch-12-avg-2-chunk-16-left-64.onnx",
                decoder = "$MODEL_DIR/decoder-epoch-12-avg-2-chunk-16-left-64.onnx",
                joiner = "$MODEL_DIR/joiner-epoch-12-avg-2-chunk-16-left-64.onnx"
            )

            val modelConfig = OnlineModelConfig(
                transducer = transducerConfig,
                tokens = "$MODEL_DIR/tokens.txt",
                numThreads = 1,
                debug = false,
                provider = "cpu",
                modelType = "zipformer2"
            )

            val featConfig = FeatureConfig(
                sampleRate = SAMPLE_RATE_HZ,
                featureDim = 80
            )

            val kwsConfig = KeywordSpotterConfig(
                featConfig = featConfig,
                modelConfig = modelConfig,
                keywordsFile = "$MODEL_DIR/keywords.txt",
                keywordsScore = 2.0f,
                keywordsThreshold = 0.15f,
                numTrailingBlanks = 1,
                maxActivePaths = 4
            )

            Log.i(TAG, "Wake phrase: $WAKE_PHRASE")
            Log.i(
                TAG,
                "Wake-word config:\nscore=${kwsConfig.keywordsScore}\nthreshold=${kwsConfig.keywordsThreshold}\ntrailingBlanks=${kwsConfig.numTrailingBlanks}\nactivePaths=${kwsConfig.maxActivePaths}"
            )

            // Step 4: Construct KeywordSpotter with AssetManager
            Log.i(TAG, "KWS STEP 6: Creating KeywordSpotter")
            val newSpotter = KeywordSpotter(
                assetManager = assetManager,
                config = kwsConfig
            )
            Log.i(TAG, "KWS STEP 7: KeywordSpotter created")

            // Step 5: Create OnlineStream
            Log.i(TAG, "KWS STEP 8: Creating OnlineStream")
            val newStream = newSpotter.createStream()
            Log.i(TAG, "KWS STEP 9: OnlineStream created")

            spotter = newSpotter
            stream = newStream

            Log.i(TAG, "KWS READY")
            return Result.success(Unit)

        } catch (e: UnsatisfiedLinkError) {
            val err = "Failed to load sherpa-onnx native library: ${e.message}"
            Log.e(TAG, err, e)
            release()
            return Result.failure(e)
        } catch (e: IllegalArgumentException) {
            val err = "Invalid KeywordSpotter parameter: ${e.message}"
            Log.e(TAG, err, e)
            release()
            return Result.failure(e)
        } catch (e: IllegalStateException) {
            val err = "Illegal state initializing KeywordSpotter: ${e.message}"
            Log.e(TAG, err, e)
            release()
            return Result.failure(e)
        } catch (e: Exception) {
            val err = "Failed to initialize KeywordSpotter: ${e.message}"
            Log.e(TAG, err, e)
            release()
            return Result.failure(e)
        } catch (t: Throwable) {
            val err = "Fatal error initializing KeywordSpotter: ${t.message}"
            Log.e(TAG, err, t)
            release()
            return Result.failure(t)
        }
    }

    /**
     * Feeds 16 kHz Mono PCM ShortArray audio samples to the active stream.
     * Converts to normalized FloatArray (sample / 32768.0f) and decodes.
     *
     * @return Detected keyword string if wake word detected, or null.
     */
    fun processAudioSamples(shortSamples: ShortArray, count: Int): String? {
        val activeStream = stream ?: return null
        val activeSpotter = spotter ?: return null

        if (count <= 0) return null

        val floatSamples = FloatArray(count)
        for (i in 0 until count) {
            floatSamples[i] = shortSamples[i] / 32768.0f
        }

        try {
            activeStream.acceptWaveform(floatSamples, SAMPLE_RATE_HZ)

            while (activeSpotter.isReady(activeStream)) {
                activeSpotter.decode(activeStream)
                val result = activeSpotter.getResult(activeStream)
                val detected = result.keyword.trim()
                if (detected.isNotBlank()) {
                    Log.i(TAG, "Wake word detected: $detected")
                    // Reset stream after detection as per sherpa-onnx kws design
                    activeSpotter.reset(activeStream)
                    return detected
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error during KeywordSpotter audio processing: ${e.message}")
        }

        return null
    }

    /**
     * Resets or recreates the OnlineStream for a new listening session.
     * Keeps the loaded KeywordSpotter in memory.
     */
    fun resetStream() {
        try {
            stream?.release()
        } catch (e: Throwable) {
            Log.w(TAG, "Error releasing old OnlineStream: ${e.message}")
        }
        stream = spotter?.createStream()
        Log.i(TAG, "SherpaOnnx OnlineStream recreated/reset")
    }

    /**
     * Releases native KeywordSpotter and OnlineStream instances cleanly.
     */
    fun release() {
        try {
            stream?.release()
        } catch (e: Throwable) {
            Log.w(TAG, "Error releasing OnlineStream: ${e.message}")
        }
        stream = null

        try {
            spotter?.release()
        } catch (e: Throwable) {
            Log.w(TAG, "Error releasing KeywordSpotter: ${e.message}")
        }
        spotter = null
        Log.i(TAG, "SherpaOnnx KeywordSpotter resources released")
    }
}
