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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * StickyNotesScreen — Suggestion #52
 * Quick memos with color coding, staggered grid, and inline editing.
 * In a full implementation, notes would be backed by Room + LiveData.
 */

data class StickyNote(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val colorIdx: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

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
    Color(0xFF39C66A),
    Color(0xFF7DB7FF),
    Color(0xFFEF4444),
    CiyatoGold,
    Color(0xFF06B6D4),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickyNotesScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    var notes by remember {
        mutableStateOf(listOf(
            StickyNote(text = "Pick up groceries\n- Eggs\n- Milk\n- Bread", colorIdx = 1),
            StickyNote(text = "Call dentist tomorrow at 10am", colorIdx = 0),
            StickyNote(text = "Book flights for July trip ✈️", colorIdx = 4),
            StickyNote(text = "Read 20 pages daily", colorIdx = 2),
        ))
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<StickyNote?>(null) }

    if (showAddDialog || editingNote != null) {
        NoteEditDialog(
            initial = editingNote?.text ?: "",
            initialColorIdx = editingNote?.colorIdx ?: 0,
            onSave = { text, colorIdx ->
                if (editingNote != null) {
                    notes = notes.map {
                        if (it.id == editingNote!!.id) it.copy(text = text, colorIdx = colorIdx) else it
                    }
                } else if (text.isNotBlank()) {
                    notes = listOf(StickyNote(text = text, colorIdx = colorIdx)) + notes
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
            TopAppBar(
                title = { Text("Notes", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = CiyatoWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CiyatoBg),
            )
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📝", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("No notes yet", color = CiyatoWhite, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("Tap + to add a quick memo", color = CiyatoMuted)
                }
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
                    onDelete = { notes = notes.filter { it.id != note.id } },
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
