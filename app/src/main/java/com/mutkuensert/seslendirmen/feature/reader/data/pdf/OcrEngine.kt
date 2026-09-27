package com.mutkuensert.seslendirmen.feature.reader.data.pdf

import android.graphics.Bitmap

interface OcrEngine {
    suspend fun recognize(bitmap: Bitmap): List<String>
    fun release()
}
