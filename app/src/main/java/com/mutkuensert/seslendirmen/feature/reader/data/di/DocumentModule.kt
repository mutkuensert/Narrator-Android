package com.mutkuensert.seslendirmen.feature.reader.data.di

import com.mutkuensert.seslendirmen.feature.reader.data.document.AssetLegalDocumentRepository
import com.mutkuensert.seslendirmen.feature.reader.data.document.LocalDocumentRepository
import com.mutkuensert.seslendirmen.feature.reader.data.pdf.ConservativeTextPreprocessor
import com.mutkuensert.seslendirmen.feature.reader.data.pdf.MlKitOcrEngine
import com.mutkuensert.seslendirmen.feature.reader.data.pdf.OcrEngine
import com.mutkuensert.seslendirmen.feature.reader.data.preferences.LastReadPositionStore
import com.mutkuensert.seslendirmen.feature.reader.domain.repository.LastReadPositionRepository
import com.mutkuensert.seslendirmen.feature.reader.domain.repository.LegalDocumentRepository
import com.mutkuensert.seslendirmen.feature.reader.domain.repository.DocumentRepository
import com.mutkuensert.seslendirmen.feature.reader.domain.repository.TextPreprocessor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DocumentModule {
    @Binds
    @Singleton
    abstract fun bindDocumentRepository(implementation: LocalDocumentRepository): DocumentRepository

    @Binds
    @Singleton
    abstract fun bindTextPreprocessor(
        implementation: ConservativeTextPreprocessor,
    ): TextPreprocessor

    @Binds
    @Singleton
    abstract fun bindOcrEngine(implementation: MlKitOcrEngine): OcrEngine

    @Binds
    @Singleton
    abstract fun bindLastReadPositionRepository(
        implementation: LastReadPositionStore,
    ): LastReadPositionRepository

    @Binds
    @Singleton
    abstract fun bindLegalDocumentRepository(
        implementation: AssetLegalDocumentRepository,
    ): LegalDocumentRepository
}
