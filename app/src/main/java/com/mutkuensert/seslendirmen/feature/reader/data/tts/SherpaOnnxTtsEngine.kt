package com.mutkuensert.seslendirmen.feature.reader.data.tts

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import com.mutkuensert.seslendirmen.feature.reader.domain.repository.TtsSettingsRepository
import com.mutkuensert.seslendirmen.feature.reader.domain.model.AudioData
import com.mutkuensert.seslendirmen.feature.reader.domain.tts.TtsEngine
import com.mutkuensert.seslendirmen.feature.reader.domain.tts.TtsEngineException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong
import kotlin.system.measureTimeMillis

@Singleton
class SherpaOnnxTtsEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: TtsSettingsRepository,
) : TtsEngine {
    private val lock = Any()
    private var tts: OfflineTts? = null

    override suspend fun initialize() = withContext(Dispatchers.Default) {
        coroutineContext.ensureActive()
        synchronized(lock) {
            if (tts != null) return@synchronized

            try {
                verifyModelAssets()
                var created: OfflineTts? = null
                val elapsedMs = measureTimeMillis {
                    val config = OfflineTtsConfig(
                        model = OfflineTtsModelConfig(
                            supertonic = OfflineTtsSupertonicModelConfig(
                                durationPredictor = modelAsset(DURATION_PREDICTOR_FILE),
                                textEncoder = modelAsset(TEXT_ENCODER_FILE),
                                vectorEstimator = modelAsset(VECTOR_ESTIMATOR_FILE),
                                vocoder = modelAsset(VOCODER_FILE),
                                ttsJson = modelAsset(TTS_CONFIG_FILE),
                                unicodeIndexer = modelAsset(UNICODE_INDEXER_FILE),
                                voiceStyle = modelAsset(VOICE_STYLE_FILE),
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
                    val generationConfig = GenerationConfig(
                        sid = 6,
                        speed = 1f,
                        numSteps = settingsRepository.readNumSteps(),
                        extra = mapOf("lang" to settingsRepository.readLanguage().code),
                    )
                    result = checkNotNull(tts).generateWithConfig(text, generationConfig)
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
            REQUIRED_MODEL_FILES.forEach { fileName ->
                context.assets.open(modelAsset(fileName)).close()
            }
        } catch (error: Throwable) {
            throw TtsEngineException(TtsEngineException.Reason.MODEL_FILES_MISSING, error)
        }
    }

    private fun modelAsset(fileName: String) = "$MODEL_ASSET_DIR/$fileName"

    private companion object {
        const val TAG = "OfflineTts"
        const val MODEL_ASSET_DIR = "tts/supertonic3"
        const val DURATION_PREDICTOR_FILE = "duration_predictor.int8.onnx"
        const val TEXT_ENCODER_FILE = "text_encoder.int8.onnx"
        const val VECTOR_ESTIMATOR_FILE = "vector_estimator.int8.onnx"
        const val VOCODER_FILE = "vocoder.int8.onnx"
        const val TTS_CONFIG_FILE = "tts.json"
        const val UNICODE_INDEXER_FILE = "unicode_indexer.bin"
        const val VOICE_STYLE_FILE = "voice.bin"
        val REQUIRED_MODEL_FILES = listOf(
            DURATION_PREDICTOR_FILE,
            TEXT_ENCODER_FILE,
            VECTOR_ESTIMATOR_FILE,
            VOCODER_FILE,
            TTS_CONFIG_FILE,
            UNICODE_INDEXER_FILE,
            VOICE_STYLE_FILE,
        )
    }
}
