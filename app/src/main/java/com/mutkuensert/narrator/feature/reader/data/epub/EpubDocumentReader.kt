package com.mutkuensert.narrator.feature.reader.data.epub

import android.content.Context
import android.net.Uri
import android.util.Log
import android.util.Xml
import com.mutkuensert.narrator.feature.reader.domain.model.Document
import com.mutkuensert.narrator.feature.reader.domain.model.DocumentFormat
import com.mutkuensert.narrator.feature.reader.domain.model.DocumentParagraph
import com.mutkuensert.narrator.feature.reader.domain.model.DocumentSection
import com.mutkuensert.narrator.feature.reader.domain.repository.DocumentExtractionProgress
import com.mutkuensert.narrator.feature.reader.domain.repository.DocumentReadException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.net.URI
import java.util.Locale
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class EpubDocumentReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun openDocument(
        uri: Uri,
        fileName: String?,
        onProgress: (DocumentExtractionProgress) -> Unit,
    ): Document = withContext(Dispatchers.IO) {
        try {
            val entries = readTextEntries(uri)
            val container = entries[CONTAINER_PATH]
                ?: throw DocumentReadException(DocumentReadException.Reason.CORRUPTED)
            val packagePath = parsePackagePath(container)
            val packageData = entries[packagePath]
                ?: throw DocumentReadException(DocumentReadException.Reason.CORRUPTED)
            val book = parsePackage(packageData, packagePath)
            val sections = book.spine.mapIndexedNotNull { position, path ->
                coroutineContext.ensureActive()
                onProgress(
                    DocumentExtractionProgress(
                        sectionNumber = position + 1,
                        sectionCount = book.spine.size,
                        stage = DocumentExtractionProgress.Stage.PARSING_EPUB,
                    ),
                )
                entries[path]?.let { content ->
                    parseSection(content, position + 1)
                }
            }.filter { it.paragraphs.isNotEmpty() }
                .mapIndexed { position, section -> section.copy(index = position + 1) }

            if (sections.isEmpty()) {
                throw DocumentReadException(DocumentReadException.Reason.NO_EXTRACTABLE_TEXT)
            }
            val title = book.title?.takeIf(String::isNotBlank)
                ?: fileName?.substringBeforeLast('.')
            Document(
                title = title,
                sections = sections,
                fileName = fileName,
                format = DocumentFormat.EPUB,
            )
        } catch (error: DocumentReadException) {
            throw error
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: FileNotFoundException) {
            throw DocumentReadException(DocumentReadException.Reason.ACCESS_DENIED, error)
        } catch (error: SecurityException) {
            throw DocumentReadException(DocumentReadException.Reason.ACCESS_DENIED, error)
        } catch (error: ZipException) {
            throw DocumentReadException(DocumentReadException.Reason.CORRUPTED, error)
        } catch (error: IOException) {
            throw DocumentReadException(DocumentReadException.Reason.CORRUPTED, error)
        } catch (error: Throwable) {
            Log.e(TAG, "Unexpected EPUB extraction failure", error)
            throw DocumentReadException(DocumentReadException.Reason.CORRUPTED, error)
        }
    }

    private suspend fun readTextEntries(uri: Uri): Map<String, ByteArray> {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw DocumentReadException(DocumentReadException.Reason.ACCESS_DENIED)
        return input.use { stream ->
            ZipInputStream(stream.buffered()).use { zip ->
                buildMap {
                    var entryCount = 0
                    var totalBytes = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val entry = zip.nextEntry ?: break
                        entryCount++
                        if (entryCount > MAX_ENTRY_COUNT) {
                            throw DocumentReadException(DocumentReadException.Reason.CORRUPTED)
                        }
                        val path = normalizeArchivePath(entry.name) ?: continue
                        if (!entry.isDirectory && isTextEntry(path)) {
                            val bytes = readBounded(zip)
                            totalBytes += bytes.size
                            if (totalBytes > MAX_TOTAL_TEXT_BYTES) {
                                throw DocumentReadException(DocumentReadException.Reason.CORRUPTED)
                            }
                            if (containsKey(path)) {
                                throw DocumentReadException(DocumentReadException.Reason.CORRUPTED)
                            }
                            put(path, bytes)
                        }
                        zip.closeEntry()
                    }
                }
            }
        }
    }

    private fun readBounded(zip: ZipInputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = zip.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_TEXT_ENTRY_BYTES) {
                throw DocumentReadException(DocumentReadException.Reason.CORRUPTED)
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun parsePackagePath(xml: ByteArray): String {
        val parser = newParser(xml)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                val path = parser.getAttributeValue(null, "full-path")
                return normalizeArchivePath(path)
                    ?: throw DocumentReadException(DocumentReadException.Reason.CORRUPTED)
            }
        }
        throw DocumentReadException(DocumentReadException.Reason.CORRUPTED)
    }

    private fun parsePackage(xml: ByteArray, packagePath: String): PackageData {
        val parser = newParser(xml)
        val manifest = mutableMapOf<String, String>()
        val spineIds = mutableListOf<String>()
        var title: String? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name.lowercase(Locale.ROOT)) {
                "title" -> if (title == null) title = parser.nextText().normalizedText()
                "item" -> {
                    val id = parser.getAttributeValue(null, "id") ?: continue
                    val href = parser.getAttributeValue(null, "href") ?: continue
                    val mediaType = parser.getAttributeValue(null, "media-type").orEmpty()
                    if (mediaType in XHTML_MEDIA_TYPES || href.hasHtmlExtension()) {
                        resolveArchivePath(packagePath, href)?.let { manifest[id] = it }
                    }
                }

                "itemref" -> if (!parser.getAttributeValue(null, "linear").equals("no", true)) {
                    parser.getAttributeValue(null, "idref")?.let(spineIds::add)
                }
            }
        }
        return PackageData(title, spineIds.mapNotNull(manifest::get))
    }

    private fun parseSection(xml: ByteArray, index: Int): DocumentSection {
        val parser = newParser(sanitizeXml(xml))
        val paragraphs = mutableListOf<DocumentParagraph>()
        var activeBlockDepth: Int? = null
        var ignoredDepth = 0
        var blockTag: String? = null
        var sectionTitle: String? = null
        val text = StringBuilder()

        fun finishBlock() {
            val value = text.toString().normalizedText()
            if (value.isNotEmpty()) {
                if (sectionTitle == null && blockTag in HEADING_TAGS) sectionTitle = value
                paragraphs += DocumentParagraph(value)
            }
            text.clear()
            blockTag = null
        }

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name?.lowercase(Locale.ROOT)
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when {
                    ignoredDepth > 0 -> ignoredDepth++
                    tag in IGNORED_TAGS -> ignoredDepth = 1
                    tag in BLOCK_TAGS -> {
                        if (activeBlockDepth != null && text.isNotBlank()) finishBlock()
                        activeBlockDepth = parser.depth
                        blockTag = tag
                        text.clear()
                    }

                    tag == "br" && activeBlockDepth != null -> text.append(' ')
                }

                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    if (ignoredDepth == 0 && activeBlockDepth != null) {
                        text.append(parser.text).append(' ')
                    }
                }

                XmlPullParser.END_TAG -> when {
                    ignoredDepth > 0 -> ignoredDepth--
                    tag in BLOCK_TAGS && activeBlockDepth == parser.depth -> {
                        finishBlock()
                        activeBlockDepth = null
                    }
                }
            }
        }
        return DocumentSection(index = index, title = sectionTitle, paragraphs = paragraphs)
    }

    private fun newParser(xml: ByteArray): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        setInput(ByteArrayInputStream(xml), null)
    }

    private fun sanitizeXml(xml: ByteArray): ByteArray {
        val charset = if (
            xml.size >= 2 &&
            ((xml[0] == 0xFF.toByte() && xml[1] == 0xFE.toByte()) ||
                    (xml[0] == 0xFE.toByte() && xml[1] == 0xFF.toByte()))
        ) {
            Charsets.UTF_16
        } else {
            Charsets.UTF_8
        }
        val source = xml.toString(charset)
            .trimStart('\uFEFF')
            .replace(XML_ENCODING, "encoding=\"UTF-8\"")
        return NAMED_ENTITY.replace(source) { match ->
            HTML_ENTITIES[match.groupValues[1]] ?: " "
        }.toByteArray(Charsets.UTF_8)
    }

    private fun resolveArchivePath(basePath: String, href: String): String? = runCatching {
        val cleanHref = href.substringBefore('#').substringBefore('?')
        val resolved = URI(null, null, basePath, null).resolve(cleanHref).normalize().path
        normalizeArchivePath(resolved)
    }.getOrNull()

    private fun normalizeArchivePath(path: String?): String? {
        val normalized = path?.replace('\\', '/')?.trimStart('/') ?: return null
        if (normalized.isBlank() || normalized.split('/').any { it == ".." }) return null
        return normalized
    }

    private fun isTextEntry(path: String): Boolean =
        path == CONTAINER_PATH || path.substringAfterLast('.', "")
            .lowercase(Locale.ROOT) in TEXT_EXTENSIONS

    private fun String.hasHtmlExtension(): Boolean =
        substringBefore('#').substringAfterLast('.', "").lowercase(Locale.ROOT) in HTML_EXTENSIONS

    private fun String.normalizedText(): String = replace(WHITESPACE, " ").trim()

    private data class PackageData(val title: String?, val spine: List<String>)

    private companion object {
        const val TAG = "EpubExtraction"
        const val CONTAINER_PATH = "META-INF/container.xml"
        const val MAX_ENTRY_COUNT = 10_000
        const val MAX_TEXT_ENTRY_BYTES = 8 * 1024 * 1024
        const val MAX_TOTAL_TEXT_BYTES = 40L * 1024 * 1024
        val TEXT_EXTENSIONS = setOf("xml", "opf", "xhtml", "html", "htm", "ncx")
        val HTML_EXTENSIONS = setOf("xhtml", "html", "htm")
        val XHTML_MEDIA_TYPES = setOf("application/xhtml+xml", "text/html")
        val BLOCK_TAGS = setOf(
            "p",
            "h1",
            "h2",
            "h3",
            "h4",
            "h5",
            "h6",
            "li",
            "blockquote",
            "figcaption",
            "dt",
            "dd",
            "pre"
        )
        val HEADING_TAGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
        val IGNORED_TAGS = setOf("script", "style", "svg", "audio", "video", "nav")
        val WHITESPACE = Regex("\\s+")
        val XML_ENCODING = Regex("encoding\\s*=\\s*['\"][^'\"]+['\"]", RegexOption.IGNORE_CASE)
        val NAMED_ENTITY = Regex("&([A-Za-z][A-Za-z0-9]+);")
        val HTML_ENTITIES = mapOf(
            "nbsp" to " ", "amp" to "&amp;", "lt" to "&lt;", "gt" to "&gt;",
            "quot" to "&quot;", "apos" to "&apos;", "ndash" to "–", "mdash" to "—",
            "hellip" to "…", "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”",
        )
    }
}
