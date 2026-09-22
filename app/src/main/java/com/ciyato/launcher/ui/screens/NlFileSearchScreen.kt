package com.ciyato.launcher.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.ciyato.launcher.data.FileAccess
import com.ciyato.launcher.data.FileSearchHistoryStore
import com.ciyato.launcher.data.FileSearchIndex
import com.ciyato.launcher.data.FileSearchIndexEntry
import com.ciyato.launcher.data.FileSearchIndexStore
import com.ciyato.launcher.ui.components.CiyatoTopBar
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import androidx.compose.ui.res.pluralStringResource
import com.ciyato.launcher.R

/**
 * NlFileSearchScreen — Suggestion #27
 * File search over the name, type, date and size of indexed files.
 *
 * It was called "Smart File Search" and it advertised "payment screenshot from
 * yesterday" and "receipt photos" (F-097, F-209). It does not read inside
 * files: no OCR, no image labels, no document text, no embeddings. Keywords are
 * matched as substrings of the FILE NAME, so "payment" could only ever match a
 * file literally named something with "payment" in it — and a screenshot is
 * named Screenshot_20260101_120000.png. The two headline examples were the two
 * the engine was least able to satisfy, which is how a working feature comes to
 * feel broken.
 *
 * The examples now shown are the ones it can actually answer, and
 * [SEARCH_EXAMPLES] is the single list the chips, the placeholder and
 * `NlFileSearchExamplesTest` all read — so an example cannot be added to the UI
 * without a test proving the engine retrieves it for the advertised reason.
 */

data class NlFileResult(
    val id: String,
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val dateMs: Long,
    val sizeBytes: Long,
    val matchReasons: List<String> = emptyList(),
)

/**
 * The examples the UI offers, and the contract behind them.
 *
 * Every one of these is retrievable by the engine for the reason it implies:
 * a word that really does appear in the file name, a type it derives, a date
 * range it computes, or a size threshold. `NlFileSearchExamplesTest` proves
 * each one against a fixture.
 *
 * "screenshot" earns its place where "payment" did not: Android names
 * screenshots Screenshot_<date>.png, so the word is in the name. That is the
 * test for whether an example belongs here.
 */
internal val SEARCH_EXAMPLES = listOf(
    "screenshot from yesterday",
    "photos from last week",
    "video from last month",
    "pdf from today",
    "large files",
)

data class ParsedQuery(
    val keywords: List<String>,
    val mimeType: String?,
    val dateRange: Pair<Long, Long>?,
    val minimumSizeBytes: Long? = null,
)

@Composable
fun NlFileSearchScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
    /**
     * Opens Files, which is what builds the internal-storage index this screen
     * searches in all-files mode. Required rather than defaulted: the recovery
     * path for F-099 must not be a no-op.
     */
    onOpenFiles: () -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val storedRoot by viewModel.filesRootUri.collectAsState()
    val selectedRoot = remember(storedRoot) {
        storedRoot.takeIf(String::isNotBlank)?.let(Uri::parse)
    }
    val fileSearchHistoryRaw by viewModel.fileSearchHistory.collectAsState()
    val saveFileSearchHistory by viewModel.saveFileSearchHistory.collectAsState()
    val fileSearchHistory = remember(fileSearchHistoryRaw) { FileSearchHistoryStore.parse(fileSearchHistoryRaw) }
    val fileSearchIndexRaw by viewModel.fileSearchIndex.collectAsState()
    val fileSearchIndex = remember(fileSearchIndexRaw) { FileSearchIndexStore.parse(fileSearchIndexRaw) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<NlFileResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    /** True when an all-files search failed for want of an index, not for want of matches. */
    var needsIndexBuild by remember { mutableStateOf(false) }
    var isSelectedFolderReadable by remember(selectedRoot) { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()
    // With All-files access there is no SAF folder to require, and Files
    // indexes internal storage instead. Without this the search screen would
    // keep asking for a folder that the person deliberately stopped using.
    val allFilesGranted = remember(storedRoot) { FileAccess.hasAllFiles(context) }

    val quickQueries = SEARCH_EXAMPLES

    LaunchedEffect(selectedRoot) {
        isSelectedFolderReadable = selectedRoot?.let { root -> isReadableTree(context, root) }
        if (selectedRoot != null && isSelectedFolderReadable == false) {
            // An index is only useful while Android still grants this exact SAF tree.
            viewModel.clearFileSearchIndex()
            results = emptyList()
            hasSearched = false
        }
    }

    suspend fun search(q: String) {
        if (q.isBlank()) return
        if (selectedRoot == null && !allFilesGranted) return
        isSearching = true
        hasSearched = false
        focusManager.clearFocus()
        try {
            // The 400 ms sleep that used to be here was theatre — it made a
            // local index lookup feel like remote work (F-094). Removing it is a
            // straight latency win; nothing depended on the pause.
            if (selectedRoot == null) {
                // All-files mode: the index Files built over internal storage is
                // the only source — there is no SAF tree to walk as a fallback.
                //
                // An unbuilt index used to produce an empty result set, which
                // renders as "No files found — try a different query". That is a
                // hidden prerequisite across two screens presented as a failed
                // search: the person rephrases their query forever while the
                // actual problem is that Files has never run (F-099). The two
                // states are distinguished now, and the recovery is offered
                // inline rather than left to be guessed.
                val usableIndex = fileSearchIndex
                    ?.takeIf { index -> index.rootUri == FileAccess.INDEX_KEY_INTERNAL }
                needsIndexBuild = usableIndex == null
                val parsed = parseNlQuery(q)
                results = usableIndex?.let { index -> searchIndexedFiles(index, parsed) }.orEmpty()
                viewModel.recordFileSearch(q)
                return
            }
            needsIndexBuild = false
            if (!isReadableTree(context, selectedRoot)) {
                isSelectedFolderReadable = false
                viewModel.clearFileSearchIndex()
                results = emptyList()
                return
            }
            isSelectedFolderReadable = true
            val parsed = parseNlQuery(q)
            results = fileSearchIndex
                ?.takeIf { index -> index.rootUri == selectedRoot.toString() }
                ?.let { index -> searchIndexedFiles(index, parsed) }
                ?: searchFiles(context, selectedRoot, parsed)
            viewModel.recordFileSearch(q)
        } finally {
            isSearching = false
            hasSearched = true
        }
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(title = "File search", onBack = onBack)
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("e.g. " + SEARCH_EXAMPLES.first(), color = CiyatoMuted, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, null, tint = CiyatoGold, modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        IconButton(onClick = { scope.launch { search(query) } }) {
                            Icon(Icons.Default.Search, "Search", tint = CiyatoSec)
                        }
                    },
                    supportingText = {
                        // The one sentence that stops someone concluding the
                        // feature is broken when it cannot find "receipt
                        // photos". It searches names, not contents, and saying
                        // so costs a line and buys the whole feature's
                        // credibility (F-097).
                        Text(
                            "Searches file names, types and dates. It does not read inside files.",
                            color = CiyatoMuted,
                            fontSize = 11.sp,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = CiyatoWhite,
                        unfocusedTextColor = CiyatoWhite,
                        focusedBorderColor = CiyatoGold,
                        unfocusedBorderColor = CiyatoBorder,
                        cursorColor = CiyatoGold,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        scope.launch { search(query) }
                    }),
                )
            }

            if (selectedRoot == null && !allFilesGranted) {
                item { FileSearchAccessCard() }
            } else if (selectedRoot != null && isSelectedFolderReadable == false) {
                item {
                    FileSearchAccessCard(
                        title = "Folder access needs attention",
                        message = "Android no longer grants Ciyato access to this selected folder. Search and its local index are paused until you choose the folder again in Files.",
                    )
                }
            } else if (!hasSearched) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text("Save file searches", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Stored only on this device. Turning this off clears them.", color = CiyatoMuted, fontSize = 11.sp)
                            }
                            Switch(checked = saveFileSearchHistory, onCheckedChange = viewModel::setSaveFileSearchHistory)
                        }
                        if (fileSearchIndex?.rootUri == selectedRoot.toString()) {
                            TextButton(onClick = viewModel::clearFileSearchIndex, modifier = Modifier.align(Alignment.End)) {
                                Text("Clear local file index", color = CiyatoSec, fontSize = 12.sp)
                            }
                        }
                    }
                }
                if (saveFileSearchHistory && fileSearchHistory.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("Recent file searches", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = viewModel::clearFileSearchHistory) {
                                Icon(Icons.Default.Delete, contentDescription = "Clear file search history", tint = CiyatoSec)
                            }
                        }
                    }
                    items(fileSearchHistory, key = { it }) { savedQuery ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(CiyatoBgEl)
                                .clickable {
                                    query = savedQuery
                                    scope.launch { search(savedQuery) }
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Search, null, tint = CiyatoMuted, modifier = Modifier.size(16.dp))
                            Text(savedQuery, color = CiyatoSec, fontSize = 13.sp)
                        }
                    }
                }
                item {
                    Text("Try these", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
                items(quickQueries, key = { it }) { q ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(CiyatoBgEl)
                            .clickable {
                                query = q
                                scope.launch { search(q) }
                            }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Search, null, tint = CiyatoMuted, modifier = Modifier.size(16.dp))
                        Text(q, color = CiyatoSec, fontSize = 13.sp)
                    }
                }
            }

            if (isSearching) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CiyatoGold, strokeWidth = 3.dp)
                    }
                }
            }

            if (hasSearched && results.isEmpty() && !isSearching && isSelectedFolderReadable != false) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (needsIndexBuild) {
                                // Not "no files found" — nothing was searched.
                                Text(
                                    "Storage hasn't been indexed yet",
                                    color = CiyatoWhite, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "With All files access, search reads an index that Files builds. " +
                                        "Open Files once to build it, then this search will work.",
                                    color = CiyatoMuted,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Open Files",
                                    color = CiyatoBg,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(CiyatoGold)
                                        .clickable { onOpenFiles() }
                                        .padding(horizontal = 18.dp, vertical = 10.dp),
                                )
                            } else {
                                Text("🔍", fontSize = 40.sp)
                                Spacer(Modifier.height(8.dp))
                                Text("No files found", color = CiyatoWhite, fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold)
                                Text("Try a different query like \"photos from last week\"", color = CiyatoMuted,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                }
            }

            if (hasSearched && results.isNotEmpty()) {
                item {
                    Text(pluralStringResource(R.plurals.count_accessible_results, results.size, results.size), color = CiyatoMuted, fontSize = 13.sp)
                }
                // Was "Top match", which implies a relevance score. Results are
                    // ordered by recency, so the label now says that (F-201).
                    item { Text("Most recent", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
                item {
                    NlFileResultRow(
                        file = results.first(),
                        onOpen = {
                            // Was wrapped in a bare runCatching that discarded the
                            // failure, so a file that could not be opened looked
                            // like a dead row (F-098). openExternally reports why.
                            FileAccess.openExternally(
                                context,
                                results.first().uri,
                                results.first().mimeType,
                            )?.let { reason ->
                                android.widget.Toast.makeText(
                                    context, reason, android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                    )
                }
                if (results.size > 1) item { Text("More matches", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
                items(results.drop(1), key = { it.id }) { file ->
                    NlFileResultRow(
                        file = file,
                        onOpen = {
                            FileAccess.openExternally(context, file.uri, file.mimeType)?.let { reason ->
                                android.widget.Toast.makeText(
                                    context, reason, android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FileSearchAccessCard(
    title: String = "Choose a folder first",
    message: String = "Internal Search only searches folders you selected in Files. It does not scan your whole device.",
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CiyatoBgEl)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Text(
            message,
            color = CiyatoSec,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
        Text("Open Files to grant or change folder access.", color = CiyatoMuted, fontSize = 12.sp)
    }
}

@Composable
private fun NlFileResultRow(file: NlFileResult, onOpen: () -> Unit) {
    val df = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val icon = when {
        file.mimeType.startsWith("image") -> "🖼"
        file.mimeType.startsWith("video") -> "🎬"
        file.mimeType.contains("pdf") -> "📄"
        file.mimeType.contains("audio") -> "🎵"
        else -> "📁"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(icon, fontSize = 28.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name, color = CiyatoWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(df.format(Date(file.dateMs)), color = CiyatoMuted, fontSize = 11.sp)
                Text(
                    file.matchReasons.joinToString(" · "),
                    color = CiyatoSec,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Text(formatBytes(file.sizeBytes), color = CiyatoSec, fontSize = 11.sp)
        }
    }
}

/**
 * Words that select a TYPE, and the type they select.
 *
 * Declared once, because the parser has to do two things with them: use them,
 * and then NOT treat them as name keywords. Those were two hand-written lists
 * that had drifted — the type list knew "photo" and the stopword list also knew
 * "photo", but neither knew "photos", so the screen's own example "photos from
 * last week" parsed into "find files whose NAME contains 'photos'" and returned
 * nothing (F-097). Deriving the second list from the first is what stops that
 * happening again, the same way WorkspacePaging stopped three copies of one
 * rule disagreeing.
 */
private val TYPE_WORDS: Map<String, String> = buildMap {
    listOf("photo", "photos", "image", "images", "picture", "pictures",
        "screenshot", "screenshots").forEach { put(it, "image") }
    listOf("video", "videos", "movie", "movies", "clip", "clips").forEach { put(it, "video") }
    listOf("pdf", "pdfs", "document", "documents", "doc", "docs")
        .forEach { put(it, "application/pdf") }
    listOf("audio", "music", "song", "songs", "recording", "recordings")
        .forEach { put(it, "audio") }
}

/** Words that set a minimum size. */
private val SIZE_WORDS = setOf("large", "big", "huge")

/** Words that carry no meaning to the engine either way. */
private val FILLER_WORDS = setOf(
    "from", "the", "with", "that", "this", "last", "next", "and", "for",
    "file", "files", "any", "all", "some", "show", "find", "search", "please",
)

/** Phrases and words consumed while working out a date range. */
private val DATE_WORDS = setOf(
    "today", "yesterday", "week", "month", "year",
    "january", "february", "march", "april", "may", "june", "july",
    "august", "september", "october", "november", "december",
)

internal fun parseNlQuery(query: String): ParsedQuery {
    val lower = query.lowercase()
    val now = System.currentTimeMillis()

    // Tokens, not substrings.
    //
    // Every test here used to be `"may" in lower`, which is true of "maybe" and
    // of any filename containing those three letters; "march" matched
    // "marching", "doc" matched "dockyard". Matching whole words removes a
    // whole family of wrong answers that were invisible because they only
    // showed up on queries nobody tried.
    val words = lower.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    val dateRange: Pair<Long, Long>? = when {
        // Calendar days, not rolling windows.
        //
        // "today" was `now - 24h .. now`, so a search at 9am returned files from
        // 3pm YESTERDAY and called them today's (F-095). Nobody means "the last
        // 24 hours" when they say today — they mean since midnight. "yesterday"
        // had the same shape one day back, so the two windows also overlapped.
        // startOfToday() anchors both to real local midnight.
        "today" in words -> startOfToday() to now
        "yesterday" in words -> {
            val todayStart = startOfToday()
            (todayStart - TimeUnit.DAYS.toMillis(1)) to todayStart
        }
        "week" in words -> (now - TimeUnit.DAYS.toMillis(7)) to now
        "month" in words -> (now - TimeUnit.DAYS.toMillis(30)) to now
        "january" in words -> dateRangeForMonth(Calendar.JANUARY)
        "february" in words -> dateRangeForMonth(Calendar.FEBRUARY)
        "march" in words -> dateRangeForMonth(Calendar.MARCH)
        "april" in words -> dateRangeForMonth(Calendar.APRIL)
        "may" in words -> dateRangeForMonth(Calendar.MAY)
        "june" in words -> dateRangeForMonth(Calendar.JUNE)
        "july" in words -> dateRangeForMonth(Calendar.JULY)
        "august" in words -> dateRangeForMonth(Calendar.AUGUST)
        "september" in words -> dateRangeForMonth(Calendar.SEPTEMBER)
        "october" in words -> dateRangeForMonth(Calendar.OCTOBER)
        "november" in words -> dateRangeForMonth(Calendar.NOVEMBER)
        "december" in words -> dateRangeForMonth(Calendar.DECEMBER)
        else -> null
    }

    val mimeType: String? = words.firstNotNullOfOrNull { TYPE_WORDS[it] }

    val minimumSizeBytes = if (words.any { it in SIZE_WORDS }) 100L * 1024L * 1024L else null

    // Whatever the query did not spend on type, date, size or filler is what
    // the person actually wants to find in the file name.
    val keywords = words.filter { word ->
        word.length > 2 &&
            word !in TYPE_WORDS &&
            word !in DATE_WORDS &&
            word !in SIZE_WORDS &&
            word !in FILLER_WORDS &&
            word.toIntOrNull() == null
    }

    return ParsedQuery(keywords, mimeType, dateRange, minimumSizeBytes)
}

/** Local midnight at the start of today. */
internal fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/**
 * The most recent occurrence of [month], which is usually what a bare month name
 * means.
 *
 * Two bugs fixed. The year was never set, so the calendar kept the CURRENT year:
 * asking for "December" in August produced a window entirely in the future and
 * therefore always zero results (F-096). And minutes, seconds and milliseconds
 * were never zeroed, so both boundaries carried the current time of day —
 * "March" started partway through 1 March and ended partway through the 31st,
 * quietly dropping files at both ends.
 */
internal fun dateRangeForMonth(month: Int): Pair<Long, Long> {
    val cal = Calendar.getInstance()
    val currentMonth = cal.get(Calendar.MONTH)
    val year = if (month > currentMonth) cal.get(Calendar.YEAR) - 1 else cal.get(Calendar.YEAR)
    cal.set(year, month, 1, 0, 0, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val start = cal.timeInMillis
    cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
    cal.set(Calendar.HOUR_OF_DAY, 23)
    cal.set(Calendar.MINUTE, 59)
    cal.set(Calendar.SECOND, 59)
    cal.set(Calendar.MILLISECOND, 999)
    return start to cal.timeInMillis
}

private suspend fun isReadableTree(context: Context, treeUri: Uri): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        DocumentFile.fromTreeUri(context, treeUri)?.canRead() == true
    }.getOrDefault(false)
}

private suspend fun searchFiles(
    context: Context,
    treeUri: Uri,
    parsed: ParsedQuery,
): List<NlFileResult> = withContext(Dispatchers.IO) {
    val root = DocumentFile.fromTreeUri(context, treeUri)
        ?.takeIf { it.canRead() }
        ?: return@withContext emptyList()
    val folders = ArrayDeque<DocumentFile>().apply { add(root) }
    val results = mutableListOf<NlFileResult>()
    var inspected = 0

    while (folders.isNotEmpty() && inspected < 2_000 && results.size < 50) {
        val folder = folders.removeFirst()
        val children = runCatching { folder.listFiles().asList() }.getOrElse { emptyList() }
        children.forEach { document ->
            if (inspected >= 2_000 || results.size >= 50) return@forEach
            inspected += 1
            when {
                document.isDirectory && document.canRead() -> folders.add(document)
                document.isFile && document.name != null && matchesSearch(document, parsed) -> {
                    results += NlFileResult(
                        id = document.uri.toString(),
                        uri = document.uri,
                        name = document.name.orEmpty(),
                        mimeType = document.type.orEmpty(),
                        dateMs = document.lastModified(),
                        sizeBytes = document.length(),
                        matchReasons = matchReasons(document, parsed),
                    )
                }
            }
        }
    }

    results.sortedWith(
        compareByDescending<NlFileResult> { it.matchReasons.size }
            .thenByDescending(NlFileResult::dateMs),
    )
}

private fun searchIndexedFiles(index: FileSearchIndex, parsed: ParsedQuery): List<NlFileResult> = index.entries
    .asSequence()
    .filter { entry -> matchesMetadata(entry.name, entry.mimeType, entry.modifiedAt, entry.sizeBytes, parsed) }
    .map { entry ->
        NlFileResult(
            id = entry.uri,
            uri = Uri.parse(entry.uri),
            name = entry.name,
            mimeType = entry.mimeType,
            dateMs = entry.modifiedAt,
            sizeBytes = entry.sizeBytes,
            matchReasons = matchReasonsMetadata(entry.name, entry.mimeType, entry.modifiedAt, entry.sizeBytes, parsed),
        )
    }
    .sortedWith(compareByDescending<NlFileResult> { it.matchReasons.size }.thenByDescending(NlFileResult::dateMs))
    .take(50)
    .toList()

private fun matchesSearch(document: DocumentFile, parsed: ParsedQuery): Boolean {
    return matchesMetadata(
        name = document.name.orEmpty(),
        mimeType = document.type.orEmpty(),
        modifiedAt = document.lastModified(),
        sizeBytes = document.length(),
        parsed = parsed,
    )
}

internal fun matchesMetadata(
    name: String,
    mimeType: String,
    modifiedAt: Long,
    sizeBytes: Long,
    parsed: ParsedQuery,
): Boolean {
    val normalizedName = name.lowercase()
    val mime = mimeType.lowercase()
    val isDocument = mime == "application/pdf" || mime.startsWith("text/") ||
        mime.contains("document") || mime.contains("spreadsheet") || mime.contains("presentation") ||
        listOf(".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx").any(normalizedName::endsWith)
    val matchesType = when (parsed.mimeType) {
        null -> true
        "image" -> mime.startsWith("image/")
        "video" -> mime.startsWith("video/")
        "audio" -> mime.startsWith("audio/")
        "application/pdf" -> isDocument
        else -> mime == parsed.mimeType
    }
    val matchesDate = parsed.dateRange?.let { (start, end) ->
        modifiedAt in start..end
    } ?: true
    val matchesSize = parsed.minimumSizeBytes?.let { sizeBytes >= it } ?: true
    val matchesKeywords = parsed.keywords.all(normalizedName::contains)
    return matchesType && matchesDate && matchesSize && matchesKeywords
}

private fun matchReasons(document: DocumentFile, parsed: ParsedQuery): List<String> = matchReasonsMetadata(
    name = document.name.orEmpty(),
    mimeType = document.type.orEmpty(),
    modifiedAt = document.lastModified(),
    sizeBytes = document.length(),
    parsed = parsed,
)

private fun matchReasonsMetadata(
    name: String,
    mimeType: String,
    modifiedAt: Long,
    sizeBytes: Long,
    parsed: ParsedQuery,
): List<String> = buildList {
    parsed.mimeType?.let { add("matches requested file type") }
    parsed.dateRange?.let { add("matches requested date") }
    parsed.minimumSizeBytes?.let { add("larger than ${formatBytes(it)}") }
    if (parsed.keywords.isNotEmpty()) add("name matches ${parsed.keywords.joinToString(", ")}")
    if (isEmpty()) add("accessible in selected folder")
}
