package com.mutkuensert.narrator.feature.reader.data.pdf

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class MlKitOcrEngine @Inject constructor() : OcrEngine {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognize(bitmap: Bitmap): List<String> =
        suspendCancellableCoroutine { continuation ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { result ->
                    if (continuation.isActive) {
                        val blocks = result.textBlocks
                            .sortedWith(
                                compareBy(
                                    { it.boundingBox?.top ?: Int.MAX_VALUE },
                                    { it.boundingBox?.left ?: Int.MAX_VALUE },
                                ),
                            )
                            .map { it.text.trim() }
                            .filter(String::isNotEmpty)
                        continuation.resume(blocks)
                    }
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
        }

    override fun release() {
        recognizer.close()
    }
}
