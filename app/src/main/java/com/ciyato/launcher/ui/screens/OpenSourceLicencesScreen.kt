package com.ciyato.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.components.QueryFailureState
import com.ciyato.launcher.ui.theme.CiyatoBg
import com.ciyato.launcher.ui.theme.CiyatoBgEl
import com.ciyato.launcher.ui.theme.CiyatoGold
import com.ciyato.launcher.ui.theme.CiyatoMuted
import com.ciyato.launcher.ui.theme.CiyatoSec
import com.ciyato.launcher.ui.theme.CiyatoSubtleBorder
import com.ciyato.launcher.ui.theme.CiyatoWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The open-source attribution Apache-2.0 section 4(d) requires Ciyato to carry.
 *
 * Reads `third_party_notices.md` from assets, which the `copyThirdPartyNotices`
 * Gradle task extracts from the marked section of `THIRD_PARTY_NOTICES.md` at build
 * time. One copy of the text exists, in the repository document, so this screen
 * cannot drift from what the project believes it depends on — which is the whole
 * objection to a hand-written licences screen, and the reason the usual answer is a
 * plugin that generates one from the dependency graph.
 *
 * That plugin is `com.google.android.gms:oss-licenses-plugin`, and it is the wrong
 * tool here: Ciyato has no Google Play Services dependency at all, so it would make
 * this screen the reason the app gains its first one. `ThirdPartyNoticesTest` closes
 * the same gap by failing the build when a dependency in `build.gradle.kts` has no
 * entry in the document.
 *
 * The rendering is deliberately plain. It is a legal notice, not a feature: the
 * markdown is parsed just far enough to give headings weight and to turn the tables
 * into readable rows, because a pipe-delimited table rendered literally on a phone is
 * unreadable, and an attribution list nobody can read is not much of an attribution.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenSourceLicencesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // Null while loading. Empty only when the asset is genuinely unreadable, which is
    // a build-wiring failure and says so rather than showing a blank page.
    var blocks by remember { mutableStateOf<List<NoticeBlock>?>(null) }

    LaunchedEffect(Unit) {
        blocks = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("third_party_notices.md").bufferedReader()
                    .use { it.readText() }
            }.map { parseNotices(it) }.getOrDefault(emptyList())
        }
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            TopAppBar(
                title = { Text("Open-source licences", color = CiyatoWhite, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = CiyatoWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CiyatoBg),
            )
        },
    ) { padding ->
        val current = blocks
        when {
            current == null -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(color = CiyatoGold)
            }

            current.isEmpty() -> QueryFailureState(
                title = "Licences could not be loaded",
                detail = "The attribution file is missing from this build. That is a packaging " +
                    "fault rather than something you can fix — the same notices are in " +
                    "THIRD_PARTY_NOTICES.md in the Ciyato repository.",
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        "Ciyato is proprietary software that incorporates the open-source " +
                            "components below, each under its own licence.",
                        color = CiyatoSec,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                items(current) { block ->
                    when (block) {
                        is NoticeBlock.Heading -> Text(
                            block.text,
                            color = CiyatoWhite,
                            fontSize = if (block.level <= 2) 17.sp else 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 10.dp),
                        )

                        is NoticeBlock.Paragraph -> Text(
                            block.text,
                            color = CiyatoSec,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )

                        is NoticeBlock.Component -> Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CiyatoBgEl)
                                .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                block.name,
                                color = CiyatoWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            if (block.coordinates.isNotBlank()) {
                                Text(
                                    block.coordinates,
                                    color = CiyatoGold,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                )
                            }
                            if (block.note.isNotBlank()) {
                                Text(
                                    block.note,
                                    color = CiyatoMuted,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One rendered piece of the notices document. */
sealed interface NoticeBlock {
    data class Heading(val text: String, val level: Int) : NoticeBlock

    data class Paragraph(val text: String) : NoticeBlock

    data class Component(val name: String, val coordinates: String, val note: String) : NoticeBlock
}

/**
 * Just enough markdown for an attribution list.
 *
 * Internal rather than private so `ThirdPartyNoticesTest` can hold it to the real
 * document instead of a fixture — a parser tested only against a string its own
 * author wrote proves nothing about the file that actually ships.
 *
 * Deliberately not a markdown library: this reads one file, of one known shape, and a
 * general parser's failure mode here is a legal notice rendered wrongly.
 */
internal fun parseNotices(markdown: String): List<NoticeBlock> {
    val blocks = mutableListOf<NoticeBlock>()
    markdown.lines().forEach { raw ->
        val line = raw.trim()
        val isTableRule = line.startsWith("|") &&
            line.all { it == '|' || it == '-' || it == ':' || it == ' ' }
        when {
            line.isEmpty() -> Unit
            line.startsWith("<!--") -> Unit
            line.startsWith("---") -> Unit
            isTableRule -> Unit

            line.startsWith("#") -> {
                val level = line.takeWhile { it == '#' }.length
                blocks += NoticeBlock.Heading(line.dropWhile { it == '#' }.trim(), level)
            }

            line.startsWith("|") -> {
                val cells = line.trim('|').split("|").map { cleanInline(it.trim()) }
                // The header row names its own columns; it is not a component.
                val isHeader = cells.firstOrNull()?.equals("Component", ignoreCase = true) == true
                if (!isHeader && cells.isNotEmpty() && cells[0].isNotBlank()) {
                    blocks += NoticeBlock.Component(
                        name = cells[0],
                        coordinates = cells.getOrElse(1) { "" },
                        note = cells.getOrElse(2) { "" },
                    )
                }
            }

            else -> blocks += NoticeBlock.Paragraph(cleanInline(line))
        }
    }
    return blocks
}

/** Strips the markdown emphasis and code ticks that would otherwise render literally. */
private fun cleanInline(s: String): String =
    s.replace("**", "").replace("`", "").trim()
