package com.mutkuensert.narrator.feature.reader.domain.repository

import com.mutkuensert.narrator.feature.reader.domain.model.LegalDocumentType

interface LegalDocumentRepository {
    suspend fun read(document: LegalDocumentType): String
}
