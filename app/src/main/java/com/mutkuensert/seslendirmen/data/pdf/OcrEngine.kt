package com.mutkuensert.seslendirmen.data.pdf

import android.graphics.Bitmap

interface OcrEngine {
    suspend fun recognize(bitmap: Bitmap): List<String>
    fun release()
}
