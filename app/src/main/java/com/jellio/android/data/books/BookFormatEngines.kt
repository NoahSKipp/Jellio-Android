package com.jellio.android.data.books

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.StringReader
import java.net.URLDecoder
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Natural alphanumeric comparison for comic/manga page filenames
 * (e.g. "page_2.jpg" < "page_10.jpg").
 */
internal object NaturalOrderComparator : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var ia = 0
        var ib = 0
        val la = a.length
        val lb = b.length
        while (ia < la && ib < lb) {
            val ca = a[ia]
            val cb = b[ib]
            if (ca.isDigit() && cb.isDigit()) {
                var za = ia
                while (za < la && a[za] == '0') za++
                var zb = ib
                while (zb < lb && b[zb] == '0') zb++

                var ea = za
                while (ea < la && a[ea].isDigit()) ea++
                var eb = zb
                while (eb < lb && b[eb].isDigit()) eb++

                val lenA = ea - za
                val lenB = eb - zb
                if (lenA != lenB) return lenA.compareTo(lenB)

                for (k in 0 until lenA) {
                    val da = a[za + k]
                    val db = b[zb + k]
                    if (da != db) return da.compareTo(db)
                }

                // Same numeric value: fewer leading zeros comes first.
                val totalA = ea - ia
                val totalB = eb - ib
                if (totalA != totalB) return totalA.compareTo(totalB)

                ia = ea
                ib = eb
            } else {
                val laChar = ca.lowercaseChar()
                val lbChar = cb.lowercaseChar()
                if (laChar != lbChar) return laChar.compareTo(lbChar)
                if (ca != cb) return ca.compareTo(cb)
                ia++
                ib++
            }
        }
        return (la - ia).compareTo(lb - ib)
    }
}

data class ComicInfo(
    val title: String? = null,
    val series: String? = null,
    val writer: String? = null,
    val number: String? = null,
    val mangaDirection: MangaReadingDirection = MangaReadingDirection.UNKNOWN,
)

enum class MangaReadingDirection {
    UNKNOWN,
    LTR,
    RTL,
}

/**
 * Native CBZ / ZIP comic archive engine backed by [ZipFile].
 */
class CbzBookEngine(val file: File) : Closeable {
    private val zip = ZipFile(file)
    private val lock = Any()

    val pageEntries: List<ZipEntry>
    val comicInfo: ComicInfo?

    val pageCount: Int get() = pageEntries.size

    init {
        val entries = mutableListOf<ZipEntry>()
        var infoEntry: ZipEntry? = null
        val enumEntries = zip.entries()
        while (enumEntries.hasMoreElements()) {
            val entry = enumEntries.nextElement()
            if (entry.isDirectory) continue
            val name = entry.name
            val baseName = name.substringAfterLast('/')
            if (name.startsWith("__MACOSX/") || baseName.startsWith("._")) continue

            if (baseName.equals("ComicInfo.xml", ignoreCase = true) && infoEntry == null) {
                infoEntry = entry
            } else if (isSupportedImage(name)) {
                entries.add(entry)
            }
        }
        entries.sortWith { e1, e2 -> NaturalOrderComparator.compare(e1.name, e2.name) }
        pageEntries = entries
        comicInfo = infoEntry?.let { parseComicInfo(it) }
    }

    fun pageName(index: Int): String? = pageEntries.getOrNull(index)?.name

    fun openPageStream(index: Int): InputStream? {
        val entry = pageEntries.getOrNull(index) ?: return null
        return synchronized(lock) {
            zip.getInputStream(entry)?.readBytes()?.inputStream()
        }
    }

    fun readPageBytes(index: Int): ByteArray? {
        val entry = pageEntries.getOrNull(index) ?: return null
        return synchronized(lock) {
            zip.getInputStream(entry)?.use { it.readBytes() }
        }
    }

    fun renderPageBitmap(index: Int, maxDimension: Int = 2048): Bitmap? {
        val bytes = readPageBytes(index) ?: return null
        return decodeSampledBitmap(bytes, maxDimension)
    }

    private fun parseComicInfo(entry: ZipEntry): ComicInfo? = runCatching {
        val xml = synchronized(lock) {
            zip.getInputStream(entry)?.use { it.readBytes().toString(Charsets.UTF_8) }
        } ?: return null

        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser().apply { setInput(StringReader(xml)) }

        var title: String? = null
        var series: String? = null
        var writer: String? = null
        var number: String? = null
        var mangaTag: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name.lowercase(Locale.US)) {
                    "title" -> title = parser.nextText().trim().ifBlank { null }
                    "series" -> series = parser.nextText().trim().ifBlank { null }
                    "writer" -> writer = parser.nextText().trim().ifBlank { null }
                    "number" -> number = parser.nextText().trim().ifBlank { null }
                    "manga" -> mangaTag = parser.nextText().trim().ifBlank { null }
                }
            }
            event = parser.next()
        }

        val direction = when (mangaTag?.lowercase(Locale.US)) {
            "yes", "yesandrighttoleft" -> MangaReadingDirection.RTL
            "no" -> MangaReadingDirection.LTR
            else -> MangaReadingDirection.UNKNOWN
        }
        ComicInfo(title = title, series = series, writer = writer, number = number, mangaDirection = direction)
    }.getOrNull()

    override fun close() {
        synchronized(lock) {
            runCatching { zip.close() }
        }
    }

    companion object {
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "avif")

        fun isSupportedImage(name: String): Boolean {
            val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
            return ext in IMAGE_EXTENSIONS
        }
    }
}

/**
 * Native PDF page renderer backed by [PdfRenderer].
 */
class PdfBookEngine(val file: File) : Closeable {
    private val pfd: ParcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer: PdfRenderer = PdfRenderer(pfd)
    private val lock = Any()

    val pageCount: Int get() = synchronized(lock) { renderer.pageCount }

    fun getPageSize(index: Int): Pair<Int, Int>? = synchronized(lock) {
        if (index < 0 || index >= renderer.pageCount) return@synchronized null
        renderer.openPage(index).use { page ->
            page.width to page.height
        }
    }

    fun renderPage(
        index: Int,
        zoom: Float = 1.0f,
        baseWidthPx: Int = 1440,
        maxDimensionPx: Int = 4096,
    ): Bitmap? = synchronized(lock) {
        if (index < 0 || index >= renderer.pageCount) return@synchronized null
        renderer.openPage(index).use { page ->
            val clampedZoom = zoom.coerceIn(0.5f, 4.0f)
            val targetBase = (baseWidthPx * clampedZoom).coerceAtLeast(320f)
            val aspect = if (page.width > 0) page.height.toFloat() / page.width.toFloat() else 1.4f
            var width = targetBase.roundToInt()
            var height = (width * aspect).roundToInt()
            val maxSide = max(width, height)
            if (maxSide > maxDimensionPx) {
                val scale = maxDimensionPx.toFloat() / maxSide.toFloat()
                width = (width * scale).roundToInt().coerceAtLeast(1)
                height = (height * scale).roundToInt().coerceAtLeast(1)
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }

    override fun close() {
        synchronized(lock) {
            runCatching { renderer.close() }
            runCatching { pfd.close() }
        }
    }
}

data class EpubTocEntry(
    val title: String,
    val href: String,
    val spineIndex: Int,
    val level: Int = 0,
)

data class EpubChapterContent(
    val spineIndex: Int,
    val href: String,
    val title: String,
    val rawHtml: String,
    val plainText: String,
    val paragraphs: List<String>,
    val imageHrefs: List<String>,
)

data class EpubSearchHit(
    val spineIndex: Int,
    val chapterTitle: String,
    val charOffset: Int,
    val locator: String,
    val snippet: String,
)

/**
 * Native EPUB parser backed by [ZipFile] and [XmlPullParser].
 * Supports OPF package manifest/spine, NCX & EPUB3 nav TOC, clean HTML
 * paragraph extraction, embedded images, full-book search, and
 * `epub:spineIndex:offset` locators.
 */
class EpubBookEngine(val file: File) : Closeable {
    private val zip = ZipFile(file)
    private val lock = Any()
    private val entryLookup: Map<String, ZipEntry>

    val title: String?
    val author: String?
    val language: String?
    val coverHref: String?
    val spineHrefs: List<String>
    val tocEntries: List<EpubTocEntry>

    val chapterCount: Int get() = spineHrefs.size

    private data class ManifestItem(
        val id: String,
        val href: String,
        val mediaType: String,
        val properties: String,
    )

    init {
        val lookup = HashMap<String, ZipEntry>()
        val enumEntries = zip.entries()
        while (enumEntries.hasMoreElements()) {
            val entry = enumEntries.nextElement()
            if (!entry.isDirectory) {
                val normalized = normalizeZipPath(entry.name)
                lookup[normalized] = entry
                lookup[normalized.lowercase(Locale.US)] = entry
            }
        }
        entryLookup = lookup

        val opfPath = parseContainerOpfPath() ?: findFirstOpfPath()
        val opfDir = opfPath?.substringBeforeLast('/', "") ?: ""
        val opfXml = opfPath?.let { readEntryText(it) }

        var parsedTitle: String? = null
        var parsedAuthor: String? = null
        var parsedLang: String? = null
        var coverId: String? = null
        var ncxId: String? = null
        val manifestById = LinkedHashMap<String, ManifestItem>()
        val spineIds = mutableListOf<String>()

        if (opfXml != null) {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newPullParser().apply { setInput(StringReader(opfXml)) }
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    val tag = parser.name?.lowercase(Locale.US) ?: ""
                    when (tag) {
                        "title" -> if (parsedTitle == null) {
                            parsedTitle = runCatching { parser.nextText().trim() }.getOrNull()?.ifBlank { null }
                        }
                        "creator" -> if (parsedAuthor == null) {
                            parsedAuthor = runCatching { parser.nextText().trim() }.getOrNull()?.ifBlank { null }
                        }
                        "language" -> if (parsedLang == null) {
                            parsedLang = runCatching { parser.nextText().trim() }.getOrNull()?.ifBlank { null }
                        }
                        "meta" -> {
                            val nameAttr = parser.getAttributeValue(null, "name")
                            val contentAttr = parser.getAttributeValue(null, "content")
                            if (nameAttr.equals("cover", ignoreCase = true) && !contentAttr.isNullOrBlank()) {
                                coverId = contentAttr.trim()
                            }
                        }
                        "item" -> {
                            val id = parser.getAttributeValue(null, "id").orEmpty()
                            val hrefRaw = parser.getAttributeValue(null, "href").orEmpty()
                            val mediaType = parser.getAttributeValue(null, "media-type").orEmpty()
                            val properties = parser.getAttributeValue(null, "properties").orEmpty()
                            if (id.isNotBlank() && hrefRaw.isNotBlank()) {
                                val resolved = resolveRelativePath(opfDir, decodeHref(hrefRaw))
                                manifestById[id] = ManifestItem(id, resolved, mediaType, properties)
                            }
                        }
                        "spine" -> {
                            ncxId = parser.getAttributeValue(null, "toc")?.ifBlank { null }
                        }
                        "itemref" -> {
                            val idref = parser.getAttributeValue(null, "idref")
                            val linear = parser.getAttributeValue(null, "linear")
                            if (!idref.isNullOrBlank() && !linear.equals("no", ignoreCase = true)) {
                                spineIds.add(idref)
                            }
                        }
                    }
                }
                event = parser.next()
            }
        }

        title = parsedTitle
        author = parsedAuthor
        language = parsedLang

        val resolvedSpine = spineIds.mapNotNull { manifestById[it]?.href }.ifEmpty {
            manifestById.values
                .filter { it.mediaType.contains("html", ignoreCase = true) }
                .map { it.href }
        }
        spineHrefs = resolvedSpine

        val spineIndexByHref = HashMap<String, Int>()
        resolvedSpine.forEachIndexed { idx, href ->
            spineIndexByHref[href.lowercase(Locale.US)] = idx
        }

        coverHref = (coverId?.let { manifestById[it] }
            ?: manifestById.values.firstOrNull { it.properties.split(' ').contains("cover-image") }
            ?: manifestById.values.firstOrNull {
                it.mediaType.startsWith("image/", ignoreCase = true) &&
                    it.id.contains("cover", ignoreCase = true)
            })?.href

        val navItem = manifestById.values.firstOrNull { it.properties.split(' ').any { prop -> prop == "nav" } }
        val ncxItem = ncxId?.let { manifestById[it] }
            ?: manifestById.values.firstOrNull { it.mediaType.equals("application/x-dtbncx+xml", ignoreCase = true) }

        val parsedToc = when {
            navItem != null -> parseNavToc(navItem.href, spineIndexByHref)
            ncxItem != null -> parseNcxToc(ncxItem.href, spineIndexByHref)
            else -> emptyList()
        }.ifEmpty {
            resolvedSpine.mapIndexed { idx, href ->
                val fallbackName = href.substringAfterLast('/').substringBeforeLast('.')
                EpubTocEntry(title = fallbackName.ifBlank { "Chapter ${idx + 1}" }, href = href, spineIndex = idx)
            }
        }
        tocEntries = parsedToc
    }

    fun getChapter(spineIndex: Int): EpubChapterContent? {
        val href = spineHrefs.getOrNull(spineIndex) ?: return null
        val rawHtml = readEntryText(href) ?: return null
        val chapterDir = href.substringBeforeLast('/', "")
        val tocTitle = tocEntries.firstOrNull { it.spineIndex == spineIndex }?.title
            ?: extractHtmlTitle(rawHtml)
            ?: "Section ${spineIndex + 1}"

        val imageHrefs = extractImageHrefs(rawHtml, chapterDir)
        val paragraphs = extractReadableParagraphs(rawHtml)
        val plainText = paragraphs.joinToString("\n\n")

        return EpubChapterContent(
            spineIndex = spineIndex,
            href = href,
            title = tocTitle,
            rawHtml = rawHtml,
            plainText = plainText,
            paragraphs = paragraphs,
            imageHrefs = imageHrefs,
        )
    }

    fun readResourceBytes(href: String): ByteArray? {
        val clean = normalizeZipPath(decodeHref(href.substringBefore('#')))
        val entry = entryLookup[clean] ?: entryLookup[clean.lowercase(Locale.US)] ?: return null
        return synchronized(lock) {
            zip.getInputStream(entry)?.use { it.readBytes() }
        }
    }

    fun decodeImageBitmap(href: String, maxDimension: Int = 1600): Bitmap? {
        val bytes = readResourceBytes(href) ?: return null
        return decodeSampledBitmap(bytes, maxDimension)
    }

    fun search(query: String, maxResults: Int = 50): List<EpubSearchHit> {
        val needle = query.trim()
        if (needle.isEmpty() || maxResults <= 0) return emptyList()
        val hits = mutableListOf<EpubSearchHit>()
        for (spineIndex in spineHrefs.indices) {
            if (hits.size >= maxResults) break
            val chapter = getChapter(spineIndex) ?: continue
            val text = chapter.plainText
            var fromIndex = 0
            while (hits.size < maxResults) {
                val matchIndex = text.indexOf(needle, startIndex = fromIndex, ignoreCase = true)
                if (matchIndex < 0) break
                val start = (matchIndex - 48).coerceAtLeast(0)
                val end = (matchIndex + needle.length + 72).coerceAtMost(text.length)
                val rawSnippet = text.substring(start, end).replace('\n', ' ').trim()
                val snippet = buildString {
                    if (start > 0) append("...")
                    append(rawSnippet)
                    if (end < text.length) append("...")
                }
                hits.add(
                    EpubSearchHit(
                        spineIndex = spineIndex,
                        chapterTitle = chapter.title,
                        charOffset = matchIndex,
                        locator = formatLocator(spineIndex, matchIndex),
                        snippet = snippet,
                    ),
                )
                fromIndex = matchIndex + needle.length.coerceAtLeast(1)
            }
        }
        return hits
    }

    fun calculateProgress(spineIndex: Int, offsetInChapter: Int = 0, chapterLength: Int = 1): Double {
        if (spineHrefs.isEmpty()) return 0.0
        val clampedSpine = spineIndex.coerceIn(0, spineHrefs.lastIndex)
        val fraction = if (chapterLength > 0) {
            (offsetInChapter.toDouble() / chapterLength.toDouble()).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        return ((clampedSpine + fraction) / spineHrefs.size.toDouble()).coerceIn(0.0, 1.0)
    }

    private fun parseContainerOpfPath(): String? {
        val containerXml = readEntryText("META-INF/container.xml") ?: return null
        return runCatching {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newPullParser().apply { setInput(StringReader(containerXml)) }
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name.equals("rootfile", ignoreCase = true)) {
                    val fullPath = parser.getAttributeValue(null, "full-path")
                    if (!fullPath.isNullOrBlank()) return normalizeZipPath(decodeHref(fullPath))
                }
                event = parser.next()
            }
            null
        }.getOrNull()
    }

    private fun findFirstOpfPath(): String? {
        return entryLookup.values.firstOrNull { it.name.endsWith(".opf", ignoreCase = true) }?.name
            ?.let(::normalizeZipPath)
    }

    private fun parseNcxToc(ncxHref: String, spineIndexByHref: Map<String, Int>): List<EpubTocEntry> {
        val xml = readEntryText(ncxHref) ?: return emptyList()
        val ncxDir = ncxHref.substringBeforeLast('/', "")
        val result = mutableListOf<EpubTocEntry>()
        runCatching {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newPullParser().apply { setInput(StringReader(xml)) }
            var depth = 0
            var currentLabel: String? = null
            var inNavLabel = false
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name?.lowercase(Locale.US) ?: ""
                        when (tag) {
                            "navpoint" -> {
                                depth++
                                currentLabel = null
                            }
                            "navlabel" -> inNavLabel = true
                            "text" -> if (inNavLabel && currentLabel == null) {
                                currentLabel = parser.nextText().trim().ifBlank { null }
                            }
                            "content" -> {
                                val src = parser.getAttributeValue(null, "src")
                                if (!src.isNullOrBlank()) {
                                    val fullHref = resolveRelativePath(ncxDir, decodeHref(src))
                                    val cleanPath = fullHref.substringBefore('#')
                                    val spineIdx = spineIndexByHref[cleanPath.lowercase(Locale.US)]
                                    if (spineIdx != null) {
                                        result.add(
                                            EpubTocEntry(
                                                title = currentLabel ?: "Chapter ${spineIdx + 1}",
                                                href = fullHref,
                                                spineIndex = spineIdx,
                                                level = (depth - 1).coerceAtLeast(0),
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val tag = parser.name?.lowercase(Locale.US) ?: ""
                        if (tag == "navlabel") inNavLabel = false
                        if (tag == "navpoint") depth = (depth - 1).coerceAtLeast(0)
                    }
                }
                event = parser.next()
            }
        }
        return result
    }

    private fun parseNavToc(navHref: String, spineIndexByHref: Map<String, Int>): List<EpubTocEntry> {
        val html = readEntryText(navHref) ?: return emptyList()
        val navDir = navHref.substringBeforeLast('/', "")
        val tocBlock = NAV_TOC_REGEX.find(html)?.value ?: html
        val result = mutableListOf<EpubTocEntry>()
        for (match in ANCHOR_REGEX.findAll(tocBlock)) {
            val hrefRaw = decodeHtmlEntities(match.groupValues[1]).trim()
            val label = stripTags(match.groupValues[2]).trim()
            if (hrefRaw.isBlank() || label.isBlank()) continue
            val fullHref = resolveRelativePath(navDir, decodeHref(hrefRaw))
            val cleanPath = fullHref.substringBefore('#')
            val spineIdx = spineIndexByHref[cleanPath.lowercase(Locale.US)] ?: continue
            result.add(
                EpubTocEntry(
                    title = label,
                    href = fullHref,
                    spineIndex = spineIdx,
                ),
            )
        }
        return result
    }

    private fun readEntryText(path: String): String? {
        val clean = normalizeZipPath(decodeHref(path.substringBefore('#')))
        val entry = entryLookup[clean] ?: entryLookup[clean.lowercase(Locale.US)] ?: return null
        return synchronized(lock) {
            zip.getInputStream(entry)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }
    }

    private fun extractImageHrefs(rawHtml: String, baseDir: String): List<String> {
        val out = LinkedHashSet<String>()
        for (match in IMG_SRC_REGEX.findAll(rawHtml)) {
            val rawSrc = match.groupValues[1].ifBlank { match.groupValues[2] }.trim()
            if (rawSrc.isNotBlank() && !rawSrc.startsWith("data:", ignoreCase = true) && !rawSrc.startsWith("http", ignoreCase = true)) {
                out.add(resolveRelativePath(baseDir, decodeHref(rawSrc)))
            }
        }
        return out.toList()
    }

    private fun extractHtmlTitle(rawHtml: String): String? {
        val heading = HEADING_REGEX.find(rawHtml)?.groupValues?.getOrNull(1)?.let(::stripTags)?.trim()
        if (!heading.isNullOrBlank()) return heading
        return TITLE_REGEX.find(rawHtml)?.groupValues?.getOrNull(1)?.let(::stripTags)?.trim()?.ifBlank { null }
    }

    private fun extractReadableParagraphs(rawHtml: String): List<String> {
        val body = BODY_REGEX.find(rawHtml)?.groupValues?.getOrNull(1) ?: rawHtml
        val cleaned = body
            .replace(SCRIPT_STYLE_REGEX, " ")
            .replace(LINE_BREAK_REGEX, "\n")
            .replace(BLOCK_CLOSE_REGEX, "\n\n")
            .replace(ALL_TAGS_REGEX, "")
        val decoded = decodeHtmlEntities(cleaned)
        return decoded
            .split(MULTI_NEWLINE_REGEX)
            .map { paragraph ->
                paragraph
                    .replace('\r', ' ')
                    .replace(HORIZONTAL_SPACE_REGEX, " ")
                    .trim()
            }
            .filter { it.isNotEmpty() }
    }

    override fun close() {
        synchronized(lock) {
            runCatching { zip.close() }
        }
    }

    companion object {
        private val NAV_TOC_REGEX = Regex("""<nav\b[^>]*epub:type\s*=\s*["'][^"']*toc[^"']*["'][^>]*>.*?</nav>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val ANCHOR_REGEX = Regex("""<a\b[^>]*href\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val IMG_SRC_REGEX = Regex("""<(?:img|image)\b[^>]*(?:src|xlink:href)\s*=\s*(?:"([^"]+)"|'([^']+)')""", RegexOption.IGNORE_CASE)
        private val HEADING_REGEX = Regex("""<h[1-3]\b[^>]*>(.*?)</h[1-3]>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val TITLE_REGEX = Regex("""<title\b[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val BODY_REGEX = Regex("""<body\b[^>]*>(.*?)</body>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val SCRIPT_STYLE_REGEX = Regex("""<(script|style|head)\b[^>]*>.*?</\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val LINE_BREAK_REGEX = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
        private val BLOCK_CLOSE_REGEX = Regex("""</(?:p|div|h[1-6]|li|blockquote|section|article|tr|pre)>""", RegexOption.IGNORE_CASE)
        private val ALL_TAGS_REGEX = Regex("""<[^>]+>""")
        private val MULTI_NEWLINE_REGEX = Regex("""\n\s*\n+""")
        private val HORIZONTAL_SPACE_REGEX = Regex("""[ \t\u00A0]+""")
        private val NUMERIC_ENTITY_REGEX = Regex("""&#(x?[0-9a-fA-F]+);""")

        fun formatLocator(spineIndex: Int, offset: Int): String = "epub:${spineIndex.coerceAtLeast(0)}:${offset.coerceAtLeast(0)}"

        fun parseLocator(locator: String): Pair<Int, Int>? {
            if (!locator.startsWith("epub:")) return null
            val parts = locator.split(':')
            if (parts.size < 3) return null
            val spine = parts[1].toIntOrNull() ?: return null
            val offset = parts[2].toIntOrNull() ?: 0
            return spine.coerceAtLeast(0) to offset.coerceAtLeast(0)
        }

        private fun stripTags(html: String): String =
            decodeHtmlEntities(html.replace(ALL_TAGS_REGEX, " ")).replace(Regex("""\s+"""), " ").trim()

        private fun decodeHtmlEntities(input: String): String {
            val basic = input
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&#39;", "'")
                .replace("&mdash;", " - ")
                .replace("&ndash;", "-")
                .replace("&hellip;", "...")
                .replace("&lsquo;", "'")
                .replace("&rsquo;", "'")
                .replace("&ldquo;", "\"")
                .replace("&rdquo;", "\"")
            return NUMERIC_ENTITY_REGEX.replace(basic) { match ->
                val raw = match.groupValues[1]
                val code = if (raw.startsWith("x", ignoreCase = true)) {
                    raw.substring(1).toIntOrNull(16)
                } else {
                    raw.toIntOrNull(10)
                }
                if (code != null && code in 1..0x10FFFF) {
                    String(Character.toChars(code))
                } else {
                    match.value
                }
            }
        }

        private fun normalizeZipPath(path: String): String {
            val cleaned = path.replace('\\', '/').trimStart('/')
            val parts = mutableListOf<String>()
            for (segment in cleaned.split('/')) {
                when (segment) {
                    "", "." -> Unit
                    ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                    else -> parts.add(segment)
                }
            }
            return parts.joinToString("/")
        }

        private fun resolveRelativePath(baseDir: String, relativeHref: String): String {
            val cleanHref = relativeHref.substringBefore('#')
            val fragment = if ('#' in relativeHref) "#" + relativeHref.substringAfter('#') else ""
            val combined = if (cleanHref.startsWith("/") || baseDir.isBlank()) {
                cleanHref
            } else {
                "$baseDir/$cleanHref"
            }
            return normalizeZipPath(combined) + fragment
        }

        private fun decodeHref(raw: String): String =
            runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
    }
}

private fun decodeSampledBitmap(bytes: ByteArray, maxDimension: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    var w = bounds.outWidth
    var h = bounds.outHeight
    while (max(w, h) > maxDimension) {
        sampleSize *= 2
        w /= 2
        h /= 2
    }
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}
