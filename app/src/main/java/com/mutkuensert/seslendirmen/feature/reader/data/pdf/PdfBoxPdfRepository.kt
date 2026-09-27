package com.mutkuensert.seslendirmen.feature.reader.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.mutkuensert.seslendirmen.feature.reader.domain.model.Document
import com.mutkuensert.seslendirmen.feature.reader.domain.model.DocumentFormat
import com.mutkuensert.seslendirmen.feature.reader.domain.model.DocumentSection
import com.mutkuensert.seslendirmen.feature.reader.domain.repository.DocumentReadException
import com.mutkuensert.seslendirmen.feature.reader.domain.repository.DocumentExtractionProgress
import com.mutkuensert.seslendirmen.feature.reader.domain.repository.TextPreprocessor
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
) {
    init {
        PDFBoxResourceLoader.init(context)
    }

    suspend fun openDocument(
        documentUri: Uri,
        fileName: String?,
        onProgress: (DocumentExtractionProgress) -> Unit,
    ): Document = withContext(Dispatchers.IO) {

        var renderDescriptor: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            val input = context.contentResolver.openInputStream(documentUri)
                ?: throw DocumentReadException(DocumentReadException.Reason.ACCESS_DENIED)
            input.use { stream ->
                PDDocument.load(stream).use { pdf ->
                    if (pdf.isEncrypted && !pdf.currentAccessPermission.canExtractContent()) {
                        throw DocumentReadException(DocumentReadException.Reason.ENCRYPTED)
                    }

                    lateinit var pages: List<DocumentSection>
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
                                DocumentExtractionProgress(
                                    pageNumber,
                                    pdf.numberOfPages,
                                    DocumentExtractionProgress.Stage.EXTRACTING_TEXT,
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
                                    DocumentExtractionProgress(
                                        pageNumber,
                                        pdf.numberOfPages,
                                        DocumentExtractionProgress.Stage.RECOGNIZING_SCAN,
                                    ),
                                )
                                val activeRenderer = renderer ?: run {
                                    renderDescriptor = context.contentResolver
                                        .openFileDescriptor(documentUri, "r")
                                        ?: throw DocumentReadException(DocumentReadException.Reason.ACCESS_DENIED)
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
                            DocumentSection(
                                index = pageNumber,
                                paragraphs = paragraphs,
                            )
                        }
                        Log.i(TAG, "OCR was used successfully for $ocrPageCount pages")
                    }

                    if (pages.none { page -> page.paragraphs.any { it.text.isNotBlank() } }) {
                        throw DocumentReadException(DocumentReadException.Reason.NO_EXTRACTABLE_TEXT)
                    }

                    val title = pdf.documentInformation.title
                        ?.trim()
                        ?.takeIf(String::isNotEmpty)
                        ?: fileName?.substringBeforeLast('.')
                    Log.i(TAG, "Extracted ${pages.size} pages in $elapsedMs ms")
                    Document(
                        title = title,
                        sections = pages,
                        fileName = fileName,
                        format = DocumentFormat.PDF,
                    )
                }
            }
        } catch (error: DocumentReadException) {
            throw error
        } catch (error: InvalidPasswordException) {
            throw DocumentReadException(DocumentReadException.Reason.ENCRYPTED, error)
        } catch (error: FileNotFoundException) {
            throw DocumentReadException(DocumentReadException.Reason.ACCESS_DENIED, error)
        } catch (error: SecurityException) {
            throw DocumentReadException(DocumentReadException.Reason.ACCESS_DENIED, error)
        } catch (error: IOException) {
            throw DocumentReadException(DocumentReadException.Reason.CORRUPTED, error)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Log.e(TAG, "Unexpected PDF extraction failure", error)
            throw DocumentReadException(DocumentReadException.Reason.UNKNOWN, error)
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
        throw DocumentReadException(DocumentReadException.Reason.OCR_FAILED, error)
    }

    private companion object {
        const val TAG = "PdfTextExtraction"
        const val OCR_DPI = 220.0
        const val PDF_POINTS_PER_INCH = 72.0
        const val MAX_OCR_PIXELS = 8_000_000
    }
}
