package com.mutkuensert.seslendirmen.data.tts

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.mutkuensert.seslendirmen.domain.model.AudioData
import com.mutkuensert.seslendirmen.domain.tts.TtsEngine
import com.mutkuensert.seslendirmen.domain.tts.TtsEngineException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToLong
import kotlin.system.measureTimeMillis

@Singleton
class SherpaOnnxTtsEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : TtsEngine {
    private val lock = Any()
    private var tts: OfflineTts? = null

    override suspend fun initialize() = withContext(Dispatchers.Default) {
        coroutineContext.ensureActive()
        synchronized(lock) {
            if (tts != null) return@synchronized

            try {
                verifyModelAssets()
                val dataDir = copyEspeakDataIfNeeded()
                var created: OfflineTts? = null
                val elapsedMs = measureTimeMillis {
                    val config = OfflineTtsConfig(
                        model = OfflineTtsModelConfig(
                            vits = OfflineTtsVitsModelConfig(
                                model = "$MODEL_ASSET_DIR/$MODEL_FILE",
                                tokens = "$MODEL_ASSET_DIR/$TOKENS_FILE",
                                dataDir = dataDir.absolutePath,
                                noiseScale = 1f,
                                noiseScaleW = 1f
                            ),
                            numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4),
                            debug = false,
                            provider = "cpu",
                        ),
                        maxNumSentences = 1,
                    )
                    created = OfflineTts(context.assets, config)
                }
                tts = checkNotNull(created)
                Log.i(TAG, "Model initialized in ${elapsedMs} ms; sampleRate=${tts?.sampleRate()}")
            } catch (error: TtsEngineException) {
                throw error
            } catch (error: Throwable) {
                Log.e(TAG, "Model initialization failed", error)
                throw TtsEngineException(TtsEngineException.Reason.INITIALIZATION_FAILED, error)
            }
        }
    }

    override suspend fun synthesize(text: String): AudioData = withContext(Dispatchers.Default) {
        require(text.isNotBlank()) { "Text must not be blank" }
        initialize()
        coroutineContext.ensureActive()

        val audio = synchronized(lock) {
            try {
                lateinit var result: GeneratedAudio
                val synthesisMs = measureTimeMillis {
                    result = checkNotNull(tts).generate(text = text, sid = 0, speed = 1.0f)
                }
                check(result.sampleRate > 0 && result.samples.isNotEmpty()) {
                    "Sherpa generated an empty audio buffer"
                }
                val audioMs = result.samples.size * 1000.0 / result.sampleRate
                val rtf = synthesisMs / audioMs
                Log.i(
                    TAG,
                    "Synthesized ${text.length} chars in ${synthesisMs} ms; " +
                        "audio=${audioMs.roundToLong()} ms; RTF=${"%.3f".format(rtf)}",
                )
                AudioData(samples = result.samples, sampleRate = result.sampleRate)
            } catch (error: Throwable) {
                Log.e(TAG, "Synthesis failed", error)
                throw TtsEngineException(TtsEngineException.Reason.SYNTHESIS_FAILED, error)
            }
        }
        coroutineContext.ensureActive()
        audio
    }

    override fun release() {
        synchronized(lock) {
            tts?.release()
            tts = null
        }
    }

    private fun verifyModelAssets() {
        try {
            context.assets.open("$MODEL_ASSET_DIR/$MODEL_FILE").close()
            context.assets.open("$MODEL_ASSET_DIR/$TOKENS_FILE").close()
            check(context.assets.list(ESPEAK_ASSET_DIR).orEmpty().isNotEmpty())
        } catch (error: Throwable) {
            throw TtsEngineException(TtsEngineException.Reason.MODEL_FILES_MISSING, error)
        }
    }

    private fun copyEspeakDataIfNeeded(): File {
        val destination = File(context.filesDir, ESPEAK_ASSET_DIR)
        val marker = File(destination, COPY_MARKER)
        if (marker.isFile && REQUIRED_ESPEAK_FILES.all { File(destination, it).isFile }) {
            return destination
        }

        destination.mkdirs()
        copyAssetTree(ESPEAK_ASSET_DIR, destination)
        check(REQUIRED_ESPEAK_FILES.all { File(destination, it).isFile }) {
            "eSpeak data copy is incomplete"
        }
        marker.writeText("sherpa-onnx-1.13.8")
        return destination
    }

    private fun copyAssetTree(assetPath: String, destination: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            destination.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }

        destination.mkdirs()
        children.forEach { child ->
            copyAssetTree("$assetPath/$child", File(destination, child))
        }
    }

    private companion object {
        const val TAG = "OfflineTurkishTts"
        const val MODEL_ASSET_DIR = "tts/tr_TR-dfki-medium"
        const val MODEL_FILE = "tr_TR-dfki-medium.onnx"
        const val TOKENS_FILE = "tokens.txt"
        const val ESPEAK_ASSET_DIR = "$MODEL_ASSET_DIR/espeak-ng-data"
        const val COPY_MARKER = ".copy-complete"
        val REQUIRED_ESPEAK_FILES = listOf("phontab", "phonindex", "phondata", "intonations")
    }
}
