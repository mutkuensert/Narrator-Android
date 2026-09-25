package com.mutkuensert.seslendirmen.di

import com.mutkuensert.seslendirmen.data.pdf.ConservativeTextPreprocessor
import com.mutkuensert.seslendirmen.data.pdf.PdfBoxPdfRepository
import com.mutkuensert.seslendirmen.domain.repository.PdfRepository
import com.mutkuensert.seslendirmen.domain.repository.TextPreprocessor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PdfModule {
    @Binds
    @Singleton
    abstract fun bindPdfRepository(implementation: PdfBoxPdfRepository): PdfRepository

    @Binds
    @Singleton
    abstract fun bindTextPreprocessor(
        implementation: ConservativeTextPreprocessor,
    ): TextPreprocessor
}
