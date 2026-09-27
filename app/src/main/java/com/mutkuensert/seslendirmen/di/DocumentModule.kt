package com.mutkuensert.seslendirmen.di

import com.mutkuensert.seslendirmen.data.document.LocalDocumentRepository
import com.mutkuensert.seslendirmen.data.pdf.ConservativeTextPreprocessor
import com.mutkuensert.seslendirmen.data.pdf.MlKitOcrEngine
import com.mutkuensert.seslendirmen.data.pdf.OcrEngine
import com.mutkuensert.seslendirmen.domain.repository.DocumentRepository
import com.mutkuensert.seslendirmen.domain.repository.TextPreprocessor
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
}
