package com.mutkuensert.seslendirmen.data.pdf

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import com.mutkuensert.seslendirmen.domain.model.PdfDocument
import com.mutkuensert.seslendirmen.domain.model.PdfPage
import com.mutkuensert.seslendirmen.domain.repository.PdfReadException
import com.mutkuensert.seslendirmen.domain.repository.PdfExtractionProgress
import com.mutkuensert.seslendirmen.domain.repository.PdfRepository
import com.mutkuensert.seslendirmen.domain.repository.TextPreprocessor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.system.measureTimeMillis

@Singleton
class PdfBoxPdfRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val textPreprocessor: TextPreprocessor,
    private val ocrEngine: OcrEngine,
) : PdfRepository {
    init {
        PDFBoxResourceLoader.init(context)
    }

    override suspend fun openDocument(
        uri: String,
        onProgress: (PdfExtractionProgress) -> Unit,
    ): PdfDocument = withContext(Dispatchers.IO) {
        val documentUri = runCatching { Uri.parse(uri) }
            .getOrElse { throw PdfReadException(PdfReadException.Reason.UNSUPPORTED_FILE, it) }
        validateMimeType(documentUri)
        persistReadAccess(documentUri)

        var renderDescriptor: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
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
                        var ocrPageCount = 0
                        pages = (1..pdf.numberOfPages).map { pageNumber ->
                            coroutineContext.ensureActive()
                            onProgress(
                                PdfExtractionProgress(
                                    pageNumber,
                                    pdf.numberOfPages,
                                    PdfExtractionProgress.Stage.EXTRACTING_TEXT,
                                ),
                            )
                            stripper.startPage = pageNumber
                            stripper.endPage = pageNumber
                            var paragraphs = textPreprocessor.preprocessPage(
                                rawText = stripper.getText(pdf),
                                pageNumber = pageNumber,
                            )
                            if (OcrFallbackPolicy.shouldRecognize(paragraphs)) {
                                onProgress(
                                    PdfExtractionProgress(
                                        pageNumber,
                                        pdf.numberOfPages,
                                        PdfExtractionProgress.Stage.RECOGNIZING_SCAN,
                                    ),
                                )
                                val activeRenderer = renderer ?: run {
                                    renderDescriptor = context.contentResolver
                                        .openFileDescriptor(documentUri, "r")
                                        ?: throw PdfReadException(PdfReadException.Reason.ACCESS_DENIED)
                                    PdfRenderer(checkNotNull(renderDescriptor)).also { renderer = it }
                                }
                                val recognizedParagraphs = recognizeScannedPage(
                                    renderer = activeRenderer,
                                    pageIndex = pageNumber - 1,
                                    pageNumber = pageNumber,
                                )
                                if (recognizedParagraphs.isNotEmpty()) {
                                    paragraphs = recognizedParagraphs
                                    ocrPageCount++
                                }
                            }
                            PdfPage(
                                pageNumber = pageNumber,
                                paragraphs = paragraphs,
                            )
                        }
                        Log.i(TAG, "OCR was used successfully for $ocrPageCount pages")
                    }

                    if (pages.none { page -> page.paragraphs.any { it.text.isNotBlank() } }) {
                        throw PdfReadException(PdfReadException.Reason.NO_EXTRACTABLE_TEXT)
                    }

                    val fileName = queryDisplayName(documentUri)
                    val title = pdf.documentInformation.title
                        ?.trim()
                        ?.takeIf(String::isNotEmpty)
                        ?: fileName?.removeSuffix(".pdf")
                    Log.i(TAG, "Extracted ${pages.size} pages in $elapsedMs ms from $title")
                    PdfDocument(title = title, pages = pages, fileName = fileName)
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Log.e(TAG, "Unexpected PDF extraction failure", error)
            throw PdfReadException(PdfReadException.Reason.UNKNOWN, error)
        } finally {
            runCatching { renderer?.close() }
            runCatching { renderDescriptor?.close() }
        }
    }

    private suspend fun recognizeScannedPage(
        renderer: PdfRenderer,
        pageIndex: Int,
        pageNumber: Int,
    ) = try {
        renderer.openPage(pageIndex).use { page ->
            val scale = OCR_DPI / PDF_POINTS_PER_INCH
            val requestedWidth = (page.width * scale).roundToInt().coerceAtLeast(1)
            val requestedHeight = (page.height * scale).roundToInt().coerceAtLeast(1)
            val pixelScale = minOf(
                1.0,
                sqrt(MAX_OCR_PIXELS.toDouble() / (requestedWidth.toLong() * requestedHeight)),
            )
            val bitmap = Bitmap.createBitmap(
                (requestedWidth * pixelScale).roundToInt().coerceAtLeast(1),
                (requestedHeight * pixelScale).roundToInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            try {
                Canvas(bitmap).drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val rawText = ocrEngine.recognize(bitmap).joinToString("\n\n")
                textPreprocessor.preprocessPage(rawText, pageNumber)
            } finally {
                bitmap.recycle()
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Log.e(TAG, "OCR failed for page $pageNumber", error)
        throw PdfReadException(PdfReadException.Reason.OCR_FAILED, error)
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
        const val OCR_DPI = 220.0
        const val PDF_POINTS_PER_INCH = 72.0
        const val MAX_OCR_PIXELS = 8_000_000
    }
}
