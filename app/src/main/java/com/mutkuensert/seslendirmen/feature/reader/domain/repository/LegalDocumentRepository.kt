package com.mutkuensert.seslendirmen.feature.reader.domain.repository

import com.mutkuensert.seslendirmen.feature.reader.domain.model.LegalDocumentType

interface LegalDocumentRepository {
    suspend fun read(document: LegalDocumentType): String
}
