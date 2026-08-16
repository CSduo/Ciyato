package com.ciyato.launcher.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ciyato.launcher.data.LauncherSettingsRepository
import com.ciyato.launcher.data.StickyNote
import com.ciyato.launcher.data.StickyNoteStore
import com.ciyato.launcher.ui.components.CiyatoEmptyState
import com.ciyato.launcher.ui.components.CiyatoTopBar
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * StickyNotesScreen — Suggestion #52
 * Quick memos with color coding, staggered grid, and inline editing.
 * Notes are persisted through [LauncherSettingsRepository] (DataStore) via
 * [StickyNoteStore], so they survive an app restart.
 */

private val NOTE_COLORS = listOf(
    Color(0xFF1E293B),
    Color(0xFF1A2E1A),
    Color(0xFF1A1A2E),
    Color(0xFF2E1A1A),
    Color(0xFF2E2A1A),
    Color(0xFF1A2A2E),
)

private val NOTE_ACCENT_COLORS = listOf(
    CiyatoSec,
    CiyatoGreen,
    CiyatoInfo,
    Color(0xFFEF4444),
    CiyatoGold,
    Color(0xFF06B6D4),
)

@Composable
fun StickyNotesScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val settingsRepo = remember { LauncherSettingsRepository(context) }
    val scope = rememberCoroutineScope()

    val notesJson by settingsRepo.stickyNotes.collectAsState(initial = "[]")
    val notes = remember(notesJson) { StickyNoteStore.parse(notesJson) }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<StickyNote?>(null) }

    if (showAddDialog || editingNote != null) {
        NoteEditDialog(
            initial = editingNote?.text ?: "",
            initialColorIdx = editingNote?.colorIdx ?: 0,
            onSave = { text, colorIdx ->
                val target = editingNote
                scope.launch {
                    val updated = when {
                        target != null -> StickyNoteStore.updateNote(notesJson, target.id, text, colorIdx)
                        text.isNotBlank() -> StickyNoteStore.addNote(notesJson, StickyNote(text = text, colorIdx = colorIdx))
                        else -> notesJson
                    }
                    settingsRepo.setStickyNotes(updated)
                }
                showAddDialog = false
                editingNote = null
            },
            onDismiss = {
                showAddDialog = false
                editingNote = null
            },
        )
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(title = "Notes", onBack = onBack)
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = CiyatoGold,
                contentColor = Color.Black,
            ) {
                Icon(Icons.Default.Add, "Add note")
            }
        }
    ) { padding ->
        if (notes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CiyatoEmptyState(
                    icon = Icons.Default.StickyNote2,
                    title = "No notes yet",
                    subtitle = "Tap + to add a quick memo",
                    actionLabel = "Add Note",
                    onAction = { showAddDialog = true },
                    modifier = Modifier.padding(32.dp),
                )
            }
            return@Scaffold
        }

        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            contentPadding = PaddingValues(
                start = 12.dp, end = 12.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = 100.dp,
            ),
            verticalItemSpacing = 10.dp,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(notes, key = { it.id }) { note ->
                NoteCard(
                    note = note,
                    onTap = { editingNote = note },
                    onDelete = {
                        scope.launch { settingsRepo.setStickyNotes(StickyNoteStore.deleteNote(notesJson, note.id)) }
                    },
                )
            }
        }
    }
}

@Composable
private fun NoteCard(note: StickyNote, onTap: () -> Unit, onDelete: () -> Unit) {
    val bg = NOTE_COLORS[note.colorIdx % NOTE_COLORS.size]
    val accent = NOTE_ACCENT_COLORS[note.colorIdx % NOTE_ACCENT_COLORS.size]
    val df = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }

    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onTap),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
            Text(note.text, color = CiyatoWhite, fontSize = 14.sp, lineHeight = 20.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(df.format(Date(note.createdAt)), color = CiyatoMuted, fontSize = 11.sp)
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, "Delete", tint = CiyatoMuted, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun NoteEditDialog(
    initial: String,
    initialColorIdx: Int,
    onSave: (String, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    var colorIdx by remember { mutableIntStateOf(initialColorIdx) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Note", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)

                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(color = CiyatoWhite, fontSize = 15.sp, lineHeight = 22.sp),
                    cursorBrush = SolidColor(CiyatoGold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F1418))
                        .padding(12.dp),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NOTE_ACCENT_COLORS.forEachIndexed { i, color ->
                        Box(
                            modifier = Modifier.size(28.dp).clip(CircleShape).background(color)
                                .then(
                                    if (colorIdx == i) Modifier.border(2.dp, CiyatoWhite, CircleShape)
                                    else Modifier
                                )
                                .clickable { colorIdx = i }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = CiyatoSec) }
                    Button(
                        onClick = { onSave(text, colorIdx) },
                        colors = ButtonDefaults.buttonColors(containerColor = CiyatoGold),
                    ) { Text("Save", color = Color.Black, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}
