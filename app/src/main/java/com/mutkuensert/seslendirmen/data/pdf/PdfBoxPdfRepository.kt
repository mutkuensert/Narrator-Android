package com.mutkuensert.seslendirmen.data.pdf

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.mutkuensert.seslendirmen.domain.model.PdfDocument
import com.mutkuensert.seslendirmen.domain.model.PdfPage
import com.mutkuensert.seslendirmen.domain.repository.PdfReadException
import com.mutkuensert.seslendirmen.domain.repository.PdfRepository
import com.mutkuensert.seslendirmen.domain.repository.TextPreprocessor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.system.measureTimeMillis

@Singleton
class PdfBoxPdfRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val textPreprocessor: TextPreprocessor,
) : PdfRepository {
    init {
        PDFBoxResourceLoader.init(context)
    }

    override suspend fun openDocument(uri: String): PdfDocument = withContext(Dispatchers.IO) {
        val documentUri = runCatching { Uri.parse(uri) }
            .getOrElse { throw PdfReadException(PdfReadException.Reason.UNSUPPORTED_FILE, it) }
        validateMimeType(documentUri)
        persistReadAccess(documentUri)

        try {
            val input = context.contentResolver.openInputStream(documentUri)
                ?: throw PdfReadException(PdfReadException.Reason.ACCESS_DENIED)
            input.use { stream ->
                PDDocument.load(stream).use { pdf ->
                    if (pdf.isEncrypted && !pdf.currentAccessPermission.canExtractContent()) {
                        throw PdfReadException(PdfReadException.Reason.ENCRYPTED)
                    }

                    lateinit var pages: List<PdfPage>
                    val elapsedMs = measureTimeMillis {
                        val stripper = PDFTextStripper().apply {
                            sortByPosition = true
                            lineSeparator = "\n"
                            paragraphStart = ""
                            paragraphEnd = "\n\n"
                            pageStart = ""
                            pageEnd = ""
                        }
                        pages = (1..pdf.numberOfPages).map { pageNumber ->
                            coroutineContext.ensureActive()
                            stripper.startPage = pageNumber
                            stripper.endPage = pageNumber
                            PdfPage(
                                pageNumber = pageNumber,
                                paragraphs = textPreprocessor.preprocessPage(
                                    rawText = stripper.getText(pdf),
                                    pageNumber = pageNumber,
                                ),
                            )
                        }
                    }

                    if (pages.none { page -> page.paragraphs.any { it.text.isNotBlank() } }) {
                        throw PdfReadException(PdfReadException.Reason.NO_EXTRACTABLE_TEXT)
                    }

                    val title = pdf.documentInformation.title
                        ?.trim()
                        ?.takeIf(String::isNotEmpty)
                        ?: queryDisplayName(documentUri)?.removeSuffix(".pdf")
                    Log.i(TAG, "Extracted ${pages.size} pages in $elapsedMs ms from $title")
                    PdfDocument(title = title, pages = pages)
                }
            }
        } catch (error: PdfReadException) {
            throw error
        } catch (error: InvalidPasswordException) {
            throw PdfReadException(PdfReadException.Reason.ENCRYPTED, error)
        } catch (error: FileNotFoundException) {
            throw PdfReadException(PdfReadException.Reason.ACCESS_DENIED, error)
        } catch (error: SecurityException) {
            throw PdfReadException(PdfReadException.Reason.ACCESS_DENIED, error)
        } catch (error: IOException) {
            throw PdfReadException(PdfReadException.Reason.CORRUPTED, error)
        } catch (error: Throwable) {
            Log.e(TAG, "Unexpected PDF extraction failure", error)
            throw PdfReadException(PdfReadException.Reason.UNKNOWN, error)
        }
    }

    private fun validateMimeType(uri: Uri) {
        val mimeType = context.contentResolver.getType(uri) ?: return
        if (mimeType != PDF_MIME_TYPE && mimeType != GENERIC_BINARY_MIME_TYPE) {
            throw PdfReadException(PdfReadException.Reason.UNSUPPORTED_FILE)
        }
    }

    private fun persistReadAccess(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure { error ->
            Log.w(TAG, "Provider did not grant persistable access for $uri", error)
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
        } finally {
            cursor?.close()
        }
    }

    private companion object {
        const val TAG = "PdfTextExtraction"
        const val PDF_MIME_TYPE = "application/pdf"
        const val GENERIC_BINARY_MIME_TYPE = "application/octet-stream"
    }
}
