package com.ciyato.launcher.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ciyato.launcher.data.PdfPageLayout
import com.ciyato.launcher.ui.components.CiyatoTopBar
import com.ciyato.launcher.ui.theme.CiyatoBg
import com.ciyato.launcher.ui.theme.CiyatoBgEl
import com.ciyato.launcher.ui.theme.CiyatoBgEl2
import com.ciyato.launcher.ui.theme.CiyatoGold
import com.ciyato.launcher.ui.theme.CiyatoMuted
import com.ciyato.launcher.ui.theme.CiyatoSubtleBorder
import com.ciyato.launcher.ui.theme.CiyatoWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Photos to PDF.
 *
 * This replaces DocumentScannerScreen, which was unreachable from anywhere in
 * the app and carried four findings (F-026 to F-029). Three were defects; the
 * fourth was its name.
 *
 * It called itself a scanner while capturing through
 * `ActivityResultContracts.TakePicturePreview()`, whose documented contract is a
 * small preview Bitmap - a thumbnail. Text-heavy pages came out unreadable in
 * the exported PDF. Real scanning also means edge detection, perspective
 * correction and contrast, none of which existed. Rather than claim a capability
 * that is not here, the feature is named for what it does: it turns photos into
 * a tidy multi-page PDF, entirely on-device.
 *
 * The other three, fixed:
 *  - Export decoded every source at full resolution and used the bitmap's own
 *    pixel dimensions as the page size. A 4000x3000 photo is ~48 MB of heap, and
 *    a document whose pages are each a different physical size prints
 *    unpredictably. Bounds are read first, the decode is sampled, and every page
 *    is A4 with the image fitted inside the margins (F-027).
 *  - The PDF was written to getExternalFilesDir - app-private, awkward to find,
 *    and deleted when Ciyato is uninstalled, while the UI reported success. The
 *    person now chooses the destination through the system file picker, so the
 *    result is an ordinary document they own (F-028).
 *  - A page was represented by its URI alone, so deleting one occurrence removed
 *    every page sharing that URI - importing the same photo twice and removing
 *    one silently removed both. Pages have identity now, and can be reordered
 *    (F-029).
 */
@Composable
fun PhotosToPdfScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Identity, not just a URI. Two pages can legitimately be the same image.
    var pages by remember { mutableStateOf<List<PdfPage>>(emptyList()) }
    var nextId by remember { mutableStateOf(0L) }
    var isExporting by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        var id = nextId
        pages = pages + uris.map { PdfPage(id = id++, uri = it) }
        nextId = id
        status = ""
    }

    // ACTION_CREATE_DOCUMENT: the person picks where it goes, and the result is
    // a normal file in a normal place that survives uninstalling Ciyato.
    val creator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { destination ->
        if (destination == null) {
            status = "Export cancelled."
            return@rememberLauncherForActivityResult
        }
        isExporting = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { writePdf(context, pages, destination) }
            isExporting = false
            status = result
        }
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(
                title = "Photos to PDF",
                subtitle = if (pages.isEmpty()) {
                    "Combine photos into one document, on this phone"
                } else {
                    "${pages.size} page${if (pages.size == 1) "" else "s"} · A4"
                },
                onBack = onBack,
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Button(
                    onClick = { picker.launch(androidx.activity.result.PickVisualMediaRequest()) },
                    enabled = !isExporting,
                    colors = ButtonDefaults.buttonColors(containerColor = CiyatoBgEl),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.PhotoLibrary, null, tint = CiyatoGold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add photos", color = CiyatoGold, fontWeight = FontWeight.SemiBold)
                }
            }

            if (pages.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(CiyatoBgEl)
                            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("No pages yet", color = CiyatoWhite, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Add photos and they become pages, in the order you arrange them. " +
                                "Each is fitted to an A4 page. Nothing is uploaded — the PDF is " +
                                "built on this phone.",
                            color = CiyatoMuted, fontSize = 12.sp, lineHeight = 17.sp,
                        )
                    }
                }
            }

            itemsIndexed(pages) { index, page ->
                PageRow(
                    page = page,
                    index = index,
                    total = pages.size,
                    enabled = !isExporting,
                    onMoveUp = { pages = pages.swapped(index, index - 1) },
                    onMoveDown = { pages = pages.swapped(index, index + 1) },
                    // Removal is by id. Filtering on the URI removed every page
                    // that shared it, which is wrong the moment someone adds the
                    // same photo twice (F-029).
                    onRemove = { pages = pages.filterNot { it.id == page.id } },
                )
            }

            if (pages.isNotEmpty()) {
                item {
                    Button(
                        onClick = { creator.launch(defaultPdfName()) },
                        enabled = !isExporting,
                        colors = ButtonDefaults.buttonColors(containerColor = CiyatoGold),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                color = CiyatoBg,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Building PDF…", color = CiyatoBg)
                        } else {
                            Icon(Icons.Default.PictureAsPdf, null, tint = CiyatoBg, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Save as PDF", color = CiyatoBg, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            if (status.isNotBlank()) {
                item { Text(status, color = CiyatoMuted, fontSize = 13.sp, lineHeight = 18.sp) }
            }
        }
    }
}

/** One page. [id] is what identifies it, not [uri]. */
private data class PdfPage(val id: Long, val uri: Uri)

private fun List<PdfPage>.swapped(from: Int, to: Int): List<PdfPage> {
    if (from !in indices || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

@Composable
private fun PageRow(
    page: PdfPage,
    index: Int,
    total: Int,
    enabled: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CiyatoBgEl)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = page.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(CiyatoBgEl2),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            "Page ${index + 1}",
            color = CiyatoWhite,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        // Explicit reorder controls rather than drag only, so this is usable
        // with TalkBack and switch access (the same reasoning as F-048).
        IconButton(onClick = onMoveUp, enabled = enabled && index > 0) {
            Icon(Icons.Default.ArrowUpward, "Move page ${index + 1} up", tint = CiyatoMuted, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onMoveDown, enabled = enabled && index < total - 1) {
            Icon(Icons.Default.ArrowDownward, "Move page ${index + 1} down", tint = CiyatoMuted, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onRemove, enabled = enabled) {
            Icon(Icons.Default.Close, "Remove page ${index + 1}", tint = CiyatoMuted, modifier = Modifier.size(18.dp))
        }
    }
}

private fun defaultPdfName(): String {
    val stamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm", java.util.Locale.getDefault())
        .format(java.util.Date())
    return "Ciyato_$stamp.pdf"
}

/**
 * Builds the document one page at a time.
 *
 * Each source is measured first, decoded at a sample factor that bounds it, drawn
 * onto a fixed A4 page, and recycled before the next is touched - so peak memory
 * is one sampled bitmap rather than all of them.
 */
private fun writePdf(
    context: android.content.Context,
    pages: List<PdfPage>,
    destination: Uri,
): String {
    if (pages.isEmpty()) return "Nothing to export."
    val document = PdfDocument()
    var written = 0
    var unreadable = 0
    try {
        pages.forEachIndexed { index, page ->
            // Bounds only: no pixels allocated.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(page.uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                unreadable++
                return@forEachIndexed
            }

            val opts = BitmapFactory.Options().apply {
                inSampleSize = PdfPageLayout.sampleSize(bounds.outWidth, bounds.outHeight)
            }
            val bitmap = context.contentResolver.openInputStream(page.uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
            if (bitmap == null) {
                unreadable++
                return@forEachIndexed
            }

            val info = PdfDocument.PageInfo
                .Builder(PdfPageLayout.A4_WIDTH_PT, PdfPageLayout.A4_HEIGHT_PT, index + 1)
                .create()
            val pdfPage = document.startPage(info)
            val place = PdfPageLayout.placeOnPage(bitmap.width, bitmap.height)
            pdfPage.canvas.drawBitmap(
                bitmap,
                null,
                android.graphics.RectF(
                    place.left,
                    place.top,
                    place.left + place.width,
                    place.top + place.height,
                ),
                null,
            )
            document.finishPage(pdfPage)
            bitmap.recycle()
            written++
        }

        if (written == 0) {
            return "None of the selected photos could be read, so nothing was saved."
        }
        context.contentResolver.openOutputStream(destination)?.use { document.writeTo(it) }
            ?: return "Could not write to the chosen location."

        val skipped = if (unreadable > 0) " $unreadable could not be read and were skipped." else ""
        return "Saved $written page${if (written == 1) "" else "s"}.$skipped"
    } catch (e: Exception) {
        return "Export failed: ${e.message ?: "unknown error"}"
    } finally {
        document.close()
    }
}
