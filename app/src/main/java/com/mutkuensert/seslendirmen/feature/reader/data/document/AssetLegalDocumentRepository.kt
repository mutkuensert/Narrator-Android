package com.mutkuensert.seslendirmen.feature.reader.data.document

import android.content.Context
import com.mutkuensert.seslendirmen.feature.reader.domain.model.LegalDocumentType
import com.mutkuensert.seslendirmen.feature.reader.domain.repository.LegalDocumentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssetLegalDocumentRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LegalDocumentRepository {
    override suspend fun read(document: LegalDocumentType): String {
        return withContext(Dispatchers.IO) {
            context.assets.open(document.assetPath()).bufferedReader().use { it.readText() }
        }
    }

    private fun LegalDocumentType.assetPath(): String = when (this) {
        LegalDocumentType.PRIVACY -> "legal/PRIVACY_POLICY.txt"
        LegalDocumentType.NOTICES -> "legal/THIRD_PARTY_NOTICES.txt"
        LegalDocumentType.MODEL_LICENSE -> "tts/supertonic3/LICENSE"
        LegalDocumentType.APACHE -> "legal/APACHE-2.0.txt"
        LegalDocumentType.ONNX_RUNTIME -> "legal/MIT-ONNXRUNTIME.txt"
        LegalDocumentType.BOUNCY_CASTLE -> "legal/BOUNCY-CASTLE.txt"
    }
}
