package com.mutkuensert.narrator.feature.reader.data.document

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.mutkuensert.narrator.feature.reader.domain.model.Document
import com.mutkuensert.narrator.feature.reader.data.epub.EpubDocumentReader
import com.mutkuensert.narrator.feature.reader.data.pdf.PdfBoxPdfRepository
import com.mutkuensert.narrator.feature.reader.domain.repository.DocumentExtractionProgress
import com.mutkuensert.narrator.feature.reader.domain.repository.DocumentReadException
import com.mutkuensert.narrator.feature.reader.domain.repository.DocumentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalDocumentRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pdfReader: PdfBoxPdfRepository,
    private val epubReader: EpubDocumentReader,
) : DocumentRepository {
    override suspend fun openDocument(
        uri: String,
        onProgress: (DocumentExtractionProgress) -> Unit,
    ): Document {
        val documentUri = runCatching { Uri.parse(uri) }
            .getOrElse { throw DocumentReadException(
                DocumentReadException.Reason.UNSUPPORTED_FILE,
                it
            )
            }
        val fileName = queryDisplayName(documentUri) ?: documentUri.lastPathSegment
        val mimeType = context.contentResolver.getType(documentUri)?.lowercase(Locale.ROOT)
        persistReadAccess(documentUri)

        return when {
            mimeType == PDF_MIME_TYPE || fileName.hasExtension("pdf") ->
                pdfReader.openDocument(documentUri, fileName, onProgress)
            mimeType == EPUB_MIME_TYPE || fileName.hasExtension("epub") ->
                epubReader.openDocument(documentUri, fileName, onProgress)
            else -> throw DocumentReadException(DocumentReadException.Reason.UNSUPPORTED_FILE)
        }
    }

    private fun persistReadAccess(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure { error ->
            Log.w(TAG, "Provider did not grant persistable document access", error)
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )
            if (cursor?.moveToFirst() == true) cursor.getString(0) else null
        } catch (error: SecurityException) {
            throw DocumentReadException(DocumentReadException.Reason.ACCESS_DENIED, error)
        } finally {
            cursor?.close()
        }
    }

    private fun String?.hasExtension(extension: String): Boolean =
        this?.substringAfterLast('.', missingDelimiterValue = "")
            ?.equals(extension, ignoreCase = true) == true

    private companion object {
        const val TAG = "DocumentRepository"
        const val PDF_MIME_TYPE = "application/pdf"
        const val EPUB_MIME_TYPE = "application/epub+zip"
    }
}