package com.ciyato.launcher.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle

/**
 * What a file is, for the purpose of drawing it.
 *
 * Every file used to be either a picture or the same grey document icon. A PDF, a
 * spreadsheet, an APK and a ZIP were indistinguishable at a glance, and a video was
 * worse than a document: Coil cannot decode video containers without an artifact this
 * app does not ship, so every video tile was an empty square.
 */
enum class FileKind { IMAGE, VIDEO, AUDIO, PDF, TEXT, WORD, SHEET, SLIDES, ARCHIVE, APK, OTHER }

private val TEXT_EXTENSIONS = setOf(
    "txt", "md", "log", "json", "xml", "csv", "tsv", "yml", "yaml", "ini", "conf",
    "kt", "java", "py", "js", "ts", "html", "css", "sh", "sql", "srt",
)

fun fileKindOf(name: String, mimeType: String): FileKind {
    val ext = name.substringAfterLast('.', "").lowercase()
    val mime = mimeType.lowercase()
    return when {
        mime.startsWith("image/") -> FileKind.IMAGE
        mime.startsWith("video/") -> FileKind.VIDEO
        mime.startsWith("audio/") -> FileKind.AUDIO
        mime == "application/pdf" || ext == "pdf" -> FileKind.PDF
        ext == "apk" || mime == "application/vnd.android.package-archive" -> FileKind.APK
        ext in setOf("doc", "docx", "odt", "rtf", "pages") -> FileKind.WORD
        ext in setOf("xls", "xlsx", "ods", "numbers") -> FileKind.SHEET
        ext in setOf("ppt", "pptx", "odp", "key") -> FileKind.SLIDES
        ext in setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz") -> FileKind.ARCHIVE
        mime.startsWith("text/") || ext in TEXT_EXTENSIONS -> FileKind.TEXT
        else -> FileKind.OTHER
    }
}

/** How a kind is drawn when there is no real preview: a glyph, a label, two colour stops. */
data class KindStyle(val icon: ImageVector, val light: Color, val deep: Color)

fun styleOf(kind: FileKind): KindStyle = when (kind) {
    FileKind.IMAGE -> KindStyle(Icons.Rounded.Image, Color(0xFFC084FC), Color(0xFF581C87))
    FileKind.VIDEO -> KindStyle(Icons.Rounded.Movie, Color(0xFFFB7185), Color(0xFF881337))
    FileKind.AUDIO -> KindStyle(Icons.Rounded.AudioFile, Color(0xFFF472B6), Color(0xFF831843))
    FileKind.PDF -> KindStyle(Icons.Rounded.PictureAsPdf, Color(0xFFF87171), Color(0xFF7F1D1D))
    FileKind.TEXT -> KindStyle(Icons.AutoMirrored.Rounded.Article, Color(0xFF94A3B8), Color(0xFF1E293B))
    FileKind.WORD -> KindStyle(Icons.Rounded.Description, Color(0xFF60A5FA), Color(0xFF1E3A8A))
    FileKind.SHEET -> KindStyle(Icons.Rounded.TableChart, Color(0xFF4ADE80), Color(0xFF14532D))
    FileKind.SLIDES -> KindStyle(Icons.Rounded.Slideshow, Color(0xFFFB923C), Color(0xFF7C2D12))
    FileKind.ARCHIVE -> KindStyle(Icons.Rounded.FolderZip, Color(0xFFFBBF24), Color(0xFF78350F))
    FileKind.APK -> KindStyle(Icons.Rounded.Android, Color(0xFF86EFAC), Color(0xFF14532D))
    FileKind.OTHER -> KindStyle(Icons.AutoMirrored.Rounded.InsertDriveFile, Color(0xFF94A3B8), Color(0xFF1E293B))
}

/**
 * Decoded previews, bounded by bytes rather than by count.
 *
 * A rendered PDF page and a video frame are each a few hundred kilobytes, and a grid
 * scrolls the same tiles in and out repeatedly; re-rendering a PDF every time a tile
 * re-enters the screen is exactly the work a cache is for. 24 MB holds a few screens.
 */
private object PreviewCache {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, value: Bitmap) { cache.put(key, value) }
}

/**
 * A real preview of a file, or an honest stand-in for one.
 *
 * - Images: the image.
 * - Videos: a decoded frame, with a play badge so it does not read as a photo.
 * - PDFs: the first page, rendered on-device with the platform's own PdfRenderer.
 * - APKs: the app's own icon, read from the archive.
 * - Text: the opening lines, set as a page.
 * - Office files and archives: a styled cover in the format's colour, because they
 *   cannot be rendered without a library, and pretending otherwise is worse than a
 *   clear cover.
 *
 * Pass [file] for a path on disk (the Files browser) and only [uri] for a MediaStore
 * item (the Organizer's categories); the loaders pick the right API for each.
 */
@Composable
fun FilePreview(
    name: String,
    mimeType: String,
    uri: Uri,
    modifier: Modifier = Modifier,
    file: File? = null,
    /** List-row size: no text snippet, smaller glyphs. */
    compact: Boolean = false,
) {
    val context = LocalContext.current
    val kind = remember(name, mimeType) { fileKindOf(name, mimeType) }
    val ext = remember(name) { name.substringAfterLast('.', "").uppercase().take(4) }
    val key = file?.path ?: uri.toString()

    Box(modifier.background(Color(0xFF15181D)), contentAlignment = Alignment.Center) {
        when (kind) {
            FileKind.IMAGE -> AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(file ?: uri)
                    // Thumbnail-sized decode. A full-resolution photo per tile is tens of
                    // megabytes for a square a few hundred pixels across.
                    .size(if (compact) 160 else 360)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            FileKind.VIDEO, FileKind.PDF, FileKind.APK -> {
                val bitmap by produceState(PreviewCache.get(key), key) {
                    if (value == null) {
                        value = loadPreview(context, kind, uri, file, name)?.also { PreviewCache.put(key, it) }
                    }
                }
                val bmp = bitmap
                when {
                    // An APK whose icon cannot be read shows the platform glyph, quietly.
                    bmp == null && kind == FileKind.APK -> Icon(
                        Icons.Rounded.Android,
                        contentDescription = null,
                        tint = Color(0xFF8B9093),
                        modifier = Modifier.size(if (compact) 24.dp else 44.dp),
                    )
                    bmp == null -> DocumentIcon(kind, ext, compact)
                    kind == FileKind.APK -> {
                        // Just the app's icon. It used to sit on a green gradient box,
                        // which read as a second icon wrapped round the real one.
                        Image(
                            bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(if (compact) 34.dp else 64.dp),
                        )
                    }
                    else -> Image(
                        bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        // A PDF page is read from the top; centring it shows the middle
                        // of the first page, which is usually body text, not the title.
                        alignment = if (kind == FileKind.PDF) Alignment.TopCenter else Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (kind == FileKind.VIDEO) PlayBadge(compact)
                if (kind == FileKind.PDF && bmp != null && !compact) TypeChip("PDF", styleOf(kind).light)
            }

            FileKind.TEXT -> if (compact) {
                DocumentIcon(kind, ext, compact = true)
            } else {
                val snippet by produceState<String?>(null, key) { value = loadSnippet(context, uri, file) }
                val text = snippet
                if (text.isNullOrBlank()) {
                    DocumentIcon(kind, ext, compact = false)
                } else {
                    TextPage(text)
                    TypeChip(ext.ifBlank { "TXT" }, styleOf(kind).light)
                }
            }

            else -> DocumentIcon(kind, ext, compact)
        }
    }
}

/** The colour a format is known by - the band on its page. */
private fun bandColor(kind: FileKind): Color = when (kind) {
    FileKind.WORD -> Color(0xFF2B6CDF)
    FileKind.SHEET -> Color(0xFF1E8E4E)
    FileKind.SLIDES -> Color(0xFFE0662B)
    FileKind.PDF -> Color(0xFFD93A3A)
    FileKind.ARCHIVE -> Color(0xFFC9951E)
    FileKind.AUDIO -> Color(0xFFC2477A)
    FileKind.VIDEO -> Color(0xFFB83254)
    FileKind.IMAGE -> Color(0xFF7C4DCC)
    FileKind.APK -> Color(0xFF2F9E5B)
    FileKind.TEXT, FileKind.OTHER -> Color(0xFF5B6675)
}

/**
 * A file that cannot be previewed, drawn the way people already recognise one: a page
 * with a folded corner, a pictogram of what is inside it, and the format's colour on a
 * band carrying its extension - the convention every desktop and file manager uses for
 * spreadsheets, documents and slides.
 *
 * This replaced a full-bleed gradient in the format's colour with a glyph on it. A
 * spreadsheet was a green box, and a screen of them looked like a screen of buttons.
 * The colour now lives on the band, where it identifies the format, and the tile stays
 * the neutral surface every other tile uses.
 */
@Composable
private fun DocumentIcon(kind: FileKind, ext: String, compact: Boolean) {
    val band = bandColor(kind)
    val measurer = rememberTextMeasurer()
    val label = ext.ifBlank { if (kind == FileKind.OTHER) "FILE" else kind.name.take(4) }
    Canvas(Modifier.fillMaxSize()) {
        val boxW = size.width
        val boxH = size.height
        val pageH = boxH * (if (compact) 0.78f else 0.56f)
        val pageW = pageH * 0.78f
        val left = (boxW - pageW) / 2f + (if (compact) 0f else pageW * 0.06f)
        val top = (boxH - pageH) / 2f
        val right = left + pageW
        val bottom = top + pageH
        val fold = pageW * 0.30f
        val r = pageW * 0.08f

        // Soft shadow so the page sits on the tile.
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.28f),
            topLeft = Offset(left + pageW * 0.03f, top + pageH * 0.04f),
            size = androidx.compose.ui.geometry.Size(pageW, pageH),
            cornerRadius = CornerRadius(r),
        )
        val page = Path().apply {
            moveTo(left + r, top)
            lineTo(right - fold, top)
            lineTo(right, top + fold)
            lineTo(right, bottom - r)
            quadraticBezierTo(right, bottom, right - r, bottom)
            lineTo(left + r, bottom)
            quadraticBezierTo(left, bottom, left, bottom - r)
            lineTo(left, top + r)
            quadraticBezierTo(left, top, left + r, top)
            close()
        }
        drawPath(page, Brush.verticalGradient(listOf(Color(0xFFF7F8FA), Color(0xFFE3E6EB)), startY = top, endY = bottom))
        // The dog-ear.
        val ear = Path().apply {
            moveTo(right - fold, top)
            lineTo(right - fold, top + fold - r * 0.6f)
            quadraticBezierTo(right - fold, top + fold, right - fold + r * 0.6f, top + fold)
            lineTo(right, top + fold)
            close()
        }
        drawPath(ear, Color(0xFFC6CBD3))

        // What is inside, in miniature.
        val ink = Color(0xFFB5BCC6)
        val inner = left + pageW * 0.18f
        val innerW = pageW * 0.64f
        val bandTop = bottom - pageH * 0.30f
        val stroke = (pageH * 0.035f).coerceAtLeast(1.5f)
        when (kind) {
            FileKind.SHEET -> {
                val gTop = top + pageH * 0.24f
                val gH = bandTop - gTop - pageH * 0.06f
                for (i in 0..3) drawLine(ink, Offset(inner, gTop + gH * i / 3f), Offset(inner + innerW, gTop + gH * i / 3f), stroke)
                for (i in 0..2) drawLine(ink, Offset(inner + innerW * i / 2f, gTop), Offset(inner + innerW * i / 2f, gTop + gH), stroke)
            }
            FileKind.SLIDES -> {
                val sTop = top + pageH * 0.24f
                drawRoundRect(
                    color = ink,
                    topLeft = Offset(inner, sTop),
                    size = androidx.compose.ui.geometry.Size(innerW, pageH * 0.24f),
                    cornerRadius = CornerRadius(stroke * 1.5f),
                )
                drawLine(ink, Offset(inner, sTop + pageH * 0.32f), Offset(inner + innerW * 0.7f, sTop + pageH * 0.32f), stroke)
            }
            FileKind.ARCHIVE -> {
                val cx = left + pageW * 0.5f
                var y = top + pageH * 0.14f
                var odd = false
                while (y < bandTop - pageH * 0.06f) {
                    val x0 = if (odd) cx else cx - pageW * 0.10f
                    drawLine(ink, Offset(x0, y), Offset(x0 + pageW * 0.10f, y), stroke * 1.4f)
                    y += pageH * 0.07f
                    odd = !odd
                }
            }
            FileKind.AUDIO -> {
                val mid = (top + pageH * 0.24f + bandTop) / 2f
                val bars = listOf(0.35f, 0.7f, 1f, 0.55f, 0.8f, 0.4f)
                val step = innerW / bars.size
                bars.forEachIndexed { i, h ->
                    val half = pageH * 0.11f * h
                    drawLine(ink, Offset(inner + step * (i + 0.5f), mid - half), Offset(inner + step * (i + 0.5f), mid + half), stroke * 1.4f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                }
            }
            else -> {
                val widths = listOf(1f, 0.86f, 1f, 0.62f)
                widths.forEachIndexed { i, w ->
                    val y = top + pageH * (0.26f + i * 0.095f)
                    if (y < bandTop - pageH * 0.04f) {
                        drawLine(ink, Offset(inner, y), Offset(inner + innerW * w, y), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    }
                }
            }
        }

        // The band: the format's colour, hanging off the page's left edge.
        val bandH = pageH * 0.20f
        val bandLeft = left - pageW * (if (compact) 0.06f else 0.14f)
        val bandW = pageW * (if (compact) 0.80f else 0.92f)
        drawRoundRect(
            color = band,
            topLeft = Offset(bandLeft, bandTop),
            size = androidx.compose.ui.geometry.Size(bandW, bandH),
            cornerRadius = CornerRadius(bandH * 0.22f),
        )
        // The label only where it can be read; at list-row size the band's colour is
        // the signal and the filename beside the icon already says the rest.
        if (!compact) {
            val style = TextStyle(
                color = Color.White,
                fontSize = (bandH * 0.52f).toSp(),
                fontWeight = FontWeight.Bold,
                letterSpacing = (bandH * 0.03f).toSp(),
            )
            val text = measurer.measure(label, style)
            drawText(
                text,
                topLeft = Offset(
                    bandLeft + (bandW - text.size.width) / 2f,
                    bandTop + (bandH - text.size.height) / 2f,
                ),
            )
        }
    }
}

/** The opening of a text file, set like a page so it reads as a document. */
@Composable
private fun TextPage(text: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F4F2))
            .padding(10.dp),
    ) {
        Text(
            text,
            color = Color(0xFF3A3F45),
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 14,
        )
    }
}

@Composable
private fun PlayBadge(compact: Boolean) {
    Box(Modifier.fillMaxSize().padding(if (compact) 4.dp else 8.dp), contentAlignment = Alignment.BottomStart) {
        Box(
            Modifier
                .size(if (compact) 18.dp else 26.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(if (compact) 13.dp else 18.dp),
            )
        }
    }
}

@Composable
private fun TypeChip(label: String, color: Color) {
    Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.BottomStart) {
        Text(
            label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.92f).compositeOverDark())
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun Color.compositeOverDark(): Color = Color(
    red = red * 0.62f,
    green = green * 0.62f,
    blue = blue * 0.62f,
    alpha = alpha,
)

// ── loaders ──────────────────────────────────────────────────────────────────

private suspend fun loadPreview(context: Context, kind: FileKind, uri: Uri, file: File?, name: String): Bitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            when (kind) {
                FileKind.VIDEO -> videoFrame(context, uri, file)
                FileKind.PDF -> pdfFirstPage(context, uri, file)
                // getPackageArchiveInfo needs a path; callers pass one for APKs.
                FileKind.APK -> apkIcon(context, file)
                else -> null
            }
        }.getOrNull()
    }

private fun videoFrame(context: Context, uri: Uri, file: File?): Bitmap? {
    // MediaStore's own thumbnail where there is one: it is cached by the system and
    // far cheaper than decoding a frame ourselves.
    if (file == null && Build.VERSION.SDK_INT >= 29) {
        runCatching { return context.contentResolver.loadThumbnail(uri, Size(384, 384), null) }
    }
    val retriever = MediaMetadataRetriever()
    return try {
        if (file != null) retriever.setDataSource(file.path) else retriever.setDataSource(context, uri)
        val frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.frameAtTime
        frame?.let { scaleDown(it, 384) }
    } finally {
        runCatching { retriever.release() }
    }
}

private fun pdfFirstPage(context: Context, uri: Uri, file: File?): Bitmap? {
    val descriptor = if (file != null) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    } else {
        context.contentResolver.openFileDescriptor(uri, "r")
    } ?: return null
    // PdfRenderer takes ownership of the descriptor and closes it with itself. An
    // encrypted or malformed PDF throws here, and the cover is the right answer then.
    val renderer = PdfRenderer(descriptor)
    try {
        if (renderer.pageCount == 0) return null
        val page = renderer.openPage(0)
        try {
            val width = 360
            val height = (width * page.height / page.width.toFloat()).toInt().coerceIn(1, width * 2)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        } finally {
            page.close()
        }
    } finally {
        renderer.close()
    }
}

private fun apkIcon(context: Context, path: File?): Bitmap? {
    if (path == null || !path.exists()) return null
    val pm = context.packageManager
    val info = pm.getPackageArchiveInfo(path.path, 0) ?: return null
    val app = info.applicationInfo ?: return null
    // Without these the icon resolves against nothing and Android returns the generic
    // robot - the very thing a real icon is meant to replace.
    app.sourceDir = path.path
    app.publicSourceDir = path.path
    return app.loadIcon(pm).toBitmap(144, 144)
}

private suspend fun loadSnippet(context: Context, uri: Uri, file: File?): String? = withContext(Dispatchers.IO) {
    runCatching {
        val stream = file?.inputStream() ?: context.contentResolver.openInputStream(uri) ?: return@runCatching null
        stream.use { input ->
            val buffer = ByteArray(1200)
            val read = input.read(buffer)
            if (read <= 0) return@runCatching null
            val text = String(buffer, 0, read, Charsets.UTF_8)
            // A NUL is the cheapest reliable sign the "text" file is binary.
            if ('\u0000' in text) null else text.replace("\r", "").take(700)
        }
    }.getOrNull()
}

private fun scaleDown(source: Bitmap, maxSide: Int): Bitmap {
    val longest = maxOf(source.width, source.height)
    if (longest <= maxSide) return source
    val ratio = maxSide.toFloat() / longest
    return Bitmap.createScaledBitmap(
        source,
        (source.width * ratio).toInt().coerceAtLeast(1),
        (source.height * ratio).toInt().coerceAtLeast(1),
        true,
    )
}

// ── folder ───────────────────────────────────────────────────────────────────

/**
 * A yellow folder, drawn rather than borrowed from the icon font.
 *
 * Material's folder glyph is a flat silhouette meant to be tinted, and tinted yellow it
 * looks like a warning sign. A file manager's folders are objects: a back panel with a
 * tab, a lighter front panel lifted over it, and a highlight along its top edge.
 */
@Composable
fun FolderGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val r = w * 0.11f
        val back = Path().apply {
            addRoundRect(RoundRect(0f, h * 0.14f, w, h * 0.90f, CornerRadius(r)))
            addRoundRect(RoundRect(0f, h * 0.06f, w * 0.44f, h * 0.30f, CornerRadius(r * 0.8f)))
        }
        drawPath(back, Brush.verticalGradient(listOf(Color(0xFFE9A92A), Color(0xFFC98512))))
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFFFFD866), Color(0xFFF6B42C)),
                startY = h * 0.28f,
                endY = h * 0.90f,
            ),
            topLeft = Offset(0f, h * 0.28f),
            size = androidx.compose.ui.geometry.Size(w, h * 0.62f),
            cornerRadius = CornerRadius(r),
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.38f),
            topLeft = Offset(w * 0.07f, h * 0.315f),
            size = androidx.compose.ui.geometry.Size(w * 0.86f, h * 0.028f),
            cornerRadius = CornerRadius(h * 0.014f),
        )
    }
}
