@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.neb.ians.ui.screens.notes

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import com.neb.ians.ui.components.NebToastHost
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import com.neb.ians.ui.components.nebHorizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.ViewAgenda
import com.neb.ians.ui.components.NebDropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neb.ians.data.local.entity.NoteEntity
import com.neb.ians.ui.components.NebEmptyState
import com.neb.ians.ui.components.NebModalSheet
import com.neb.ians.util.TactileType
import com.neb.ians.util.rememberTactileFeedback
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun NotesScreen(
    onOpenNote: (String) -> Unit,
    onNewNote: (dictate: Boolean) -> Unit,
    onOpenVoice: () -> Unit,
    onOpenAbout: () -> Unit = {},
    isDarkMode: Boolean = false,
    onToggleTheme: (() -> Unit)? = null,
    vm: NotesViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val c = MaterialTheme.colorScheme
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val tactile = rememberTactileFeedback()
    var toneSheet by remember { mutableStateOf(false) }
    var templateSheet by remember { mutableStateOf(false) }
    val selecting = ui.selection.isNotEmpty()
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val gridState = androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState()
    val isGridDragged by gridState.interactionSource.collectIsDraggedAsState()
    androidx.compose.runtime.LaunchedEffect(isGridDragged) {
        if (isGridDragged) focus.clearFocus()
    }
    com.neb.ians.ui.components.NebResetScrollOnFilterChange(
        staggeredState = gridState,
        filterKey = "${ui.filter}|${ui.tag.orEmpty()}|${ui.sort}|${ui.query.trim()}",
        dataKey = ui.notes.firstOrNull()?.id,
    )
    androidx.activity.compose.BackHandler(enabled = selecting) {
        vm.clearSelection()
    }

    fun undoable(message: String, ids: List<String>, undo: (List<String>) -> Unit) {
        scope.launch {
            snack.currentSnackbarData?.dismiss()
            val r = snack.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) undo(ids)
        }
    }

    Scaffold(
        containerColor = c.surface,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(Modifier.fillMaxWidth().background(c.surface).statusBarsPadding()) {
                if (selecting) {
                    SelectionBar(
                        count = ui.selection.size,
                        inBin = ui.filter == NoteFilter.Trash,
                        inArchive = ui.filter == NoteFilter.Archive,
                        onClose = vm::clearSelection,
                        onAll = vm::selectAll,
                        onPin = { vm.pinSelected() },
                        onTone = { toneSheet = true },
                        onArchive = {
                            val unarchive = ui.filter == NoteFilter.Archive
                            val ids = vm.archiveSelected()
                            undoable(if (unarchive) "Moved to notes" else "Archived", ids) { if (unarchive) vm.archive(it) else vm.unarchive(it) }
                        },
                        onTrash = { val ids = vm.trashSelected(); undoable("Moved to bin", ids) { vm.restore(it) } },
                        onRestore = { vm.restoreSelected() },
                        onDeleteForever = vm::deleteSelectedForever,
                    )
                } else {
                    Header(ui, vm, onOpenVoice, onOpenAbout, isDarkMode, onToggleTheme)
                }
                FilterRow(ui, vm, onClearFocus = { focus.clearFocus() })
            }
        },
        snackbarHost = { NebToastHost(snack, Modifier.padding(bottom = 76.dp)) },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            if (ui.filter == NoteFilter.Trash && ui.notes.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { focus.clearFocus(); vm.emptyBin() }) { Text("Empty bin", color = c.error) }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    !ui.loaded -> Unit
                    ui.notes.isEmpty() && ui.query.isBlank() && ui.tag == null && ui.filter == NoteFilter.All -> NotesEmptyState(
                        onWrite = { focus.clearFocus(); tactile.perform(TactileType.LightTap); templateSheet = true },
                        onDictate = { focus.clearFocus(); tactile.perform(TactileType.LightTap); onNewNote(true) },
                        modifier = Modifier.align(Alignment.Center),
                    )
                    ui.notes.isEmpty() -> NebEmptyState(
                        icon = if (ui.query.isNotBlank()) Icons.Outlined.Search else Icons.Outlined.EditNote,
                        title = when {
                            ui.query.isNotBlank() || ui.tag != null -> "No matches"
                            ui.filter == NoteFilter.Trash -> "The bin is empty"
                            ui.filter == NoteFilter.Archive -> "Nothing archived"
                            else -> "Nothing pinned"
                        },
                        modifier = Modifier.align(Alignment.Center),
                    )
                    else -> {
                        LazyVerticalStaggeredGrid(
                            state = gridState,
                            columns = if (ui.grid) StaggeredGridCells.Adaptive(156.dp) else StaggeredGridCells.Fixed(1),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 160.dp),
                            verticalItemSpacing = 8.dp,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                        items(ui.notes, key = { it.id }, contentType = { "note" }) { note ->
                            NoteCard(
                                note = note,
                                selected = note.id in ui.selection,
                                compact = ui.grid,
                                onClick = { focus.clearFocus(); if (selecting) vm.toggleSelect(note.id) else onOpenNote(note.id) },
                                onLongClick = { focus.clearFocus(); tactile.perform(TactileType.LightTap); vm.toggleSelect(note.id) },
                            )
                        }
                        }
                        if (!selecting) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                                    .padding(bottom = 18.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                NoteTile(
                                    onClick = { focus.clearFocus(); tactile.perform(TactileType.LightTap); templateSheet = true },
                                    modifier = Modifier.height(52.dp),
                                    shape = joinStart(26.dp, JoinEdge),
                                    container = c.primary,
                                    content = c.onPrimary,
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 22.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(Icons.Outlined.Add, null, modifier = Modifier.size(20.dp))
                                        Text("Write", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                }
                                NoteTile(
                                    onClick = { focus.clearFocus(); tactile.perform(TactileType.LightTap); onNewNote(true) },
                                    modifier = Modifier.height(52.dp),
                                    shape = joinEnd(26.dp, JoinEdge),
                                    container = c.secondaryContainer,
                                    content = c.onSecondaryContainer,
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 20.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(Icons.Outlined.Mic, null, modifier = Modifier.size(19.dp))
                                        Text("Dictate", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (templateSheet) {
        NoteSheet(onDismiss = { templateSheet = false }, title = "New note") {
            TemplateGrid(onPick = { t ->
                NoteTemplate.pending = t
                templateSheet = false
                onNewNote(false)
            })
        }
    }

    if (toneSheet) {
        NoteSheet(onDismiss = { toneSheet = false }, title = "Colour") {
            ToneSwatches(current = -1, onPick = { vm.toneSelected(it); toneSheet = false })
        }
    }
}

private fun templateIcon(t: NoteTemplate) = when (t) {
    NoteTemplate.Blank -> Icons.Outlined.Notes
    NoteTemplate.Class -> Icons.Outlined.School
    NoteTemplate.Tasks -> Icons.Outlined.Checklist
    NoteTemplate.Exam -> Icons.Outlined.Quiz
    NoteTemplate.Week -> Icons.Outlined.CalendarMonth
    NoteTemplate.Reading -> Icons.Outlined.MenuBook
    NoteTemplate.Journal -> Icons.Outlined.EditNote
    NoteTemplate.Outline -> Icons.Outlined.AccountTree
    NoteTemplate.Meeting -> Icons.Outlined.Groups
    NoteTemplate.Cornell -> Icons.Outlined.VerticalSplit
    NoteTemplate.Project -> Icons.Outlined.Flag
    NoteTemplate.Vocab -> Icons.Outlined.Translate
}

/** Where a note starts: a blank page, or one already shaped like what it is for. */
@Composable
private fun TemplateGrid(onPick: (NoteTemplate) -> Unit) {
    val c = MaterialTheme.colorScheme
    val rows = remember { NoteTemplate.entries.chunked(2) }
    Column(
        Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        rows.forEachIndexed { r, pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                pair.forEachIndexed { col, t ->
                    val blank = t == NoteTemplate.Blank
                    val shape = gridCellShape(r, col, rows.size, pair.size, outer = TileRadius, inner = JoinEdge)
                    NoteTile(
                        { onPick(t) },
                        Modifier.weight(1f),
                        shape = shape,
                        container = if (blank) c.primaryContainer else noteRaisedTileColor(),
                        content = if (blank) c.onPrimaryContainer else c.onSurface,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(if (blank) c.primary else noteSurfaceTileColor()),
                                contentAlignment = Alignment.Center,
                            ) { Icon(templateIcon(t), null, tint = if (blank) c.onPrimary else c.primary, modifier = Modifier.size(22.dp)) }
                            Text(
                                t.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                                color = if (blank) c.onPrimaryContainer else c.onSurface, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Header(
    ui: NotesUi,
    vm: NotesViewModel,
    onOpenVoice: () -> Unit,
    onOpenAbout: () -> Unit = {},
    isDarkMode: Boolean = false,
    onToggleTheme: (() -> Unit)? = null,
) {
    var sortMenu by remember { mutableStateOf(false) }
    val query by vm.queryFlow.collectAsStateWithLifecycle()
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val toolCount = if (onToggleTheme != null) 5 else 4
    com.neb.ians.ui.components.NebMorphTitleSearch(
        title = "Note",
        query = query,
        onQueryChange = vm::setQuery,
        placeholder = "Search notes",
        hasResults = !ui.loaded || ui.notes.isNotEmpty() || query.isBlank(),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 14.dp, top = 8.dp, bottom = 6.dp),
        trailingTools = {
            Row(
                modifier = Modifier.padding(start = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                HeaderTool(
                    icon = if (ui.grid) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView,
                    label = if (ui.grid) "List view" else "Grid view",
                    on = false,
                    shape = rowItemShape(0, toolCount, outer = 22.dp, inner = TightJoinEdge),
                    onClick = { focus.clearFocus(); vm.toggleGrid() },
                )
                Box {
                    HeaderTool(
                        icon = Icons.Outlined.Sort,
                        label = "Sort",
                        on = sortMenu,
                        shape = rowItemShape(1, toolCount, outer = 22.dp, inner = TightJoinEdge),
                        onClick = { focus.clearFocus(); sortMenu = true },
                    )
                    NebDropdownMenu(
                        expanded = sortMenu, onDismissRequest = { sortMenu = false },
                    ) {
                        NoteSort.entries.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.label) },
                                onClick = { vm.setSort(s); sortMenu = false },
                                trailingIcon = { if (ui.sort == s) Icon(Icons.Outlined.Check, null, Modifier.size(18.dp)) },
                            )
                        }
                    }
                }
                HeaderTool(
                    icon = Icons.Outlined.GraphicEq,
                    label = "Voice",
                    on = false,
                    shape = rowItemShape(2, toolCount, outer = 22.dp, inner = TightJoinEdge),
                    onClick = { focus.clearFocus(); onOpenVoice() },
                )
                if (onToggleTheme != null) {
                    HeaderTool(
                        icon = if (isDarkMode) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                        label = if (isDarkMode) "Light theme" else "Dark theme",
                        on = false,
                        shape = rowItemShape(3, toolCount, outer = 22.dp, inner = TightJoinEdge),
                        onClick = { focus.clearFocus(); onToggleTheme() },
                    )
                }
                HeaderTool(
                    icon = Icons.Outlined.Info,
                    label = "About",
                    on = false,
                    shape = rowItemShape(toolCount - 1, toolCount, outer = 22.dp, inner = TightJoinEdge),
                    onClick = { focus.clearFocus(); onOpenAbout() },
                )
            }
        }
    )
}

/** A 40dp connected M3X tonal tool: surface tile at rest, secondaryContainer while active. */
@Composable
private fun HeaderTool(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    on: Boolean,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(14.dp),
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val bg by androidx.compose.animation.animateColorAsState(if (on) c.secondaryContainer else c.surfaceContainerLow, label = "toolBg")
    Box(
        Modifier.size(width = 44.dp, height = 44.dp).clip(shape).background(bg).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = if (on) c.onSecondaryContainer else c.onSurface, modifier = Modifier.size(21.dp)) }
}

/** No notes yet (Image 4): stacked tonal cards, title, and connected [ + Write | Dictate ] pills. */
@Composable
private fun NotesEmptyState(onWrite: () -> Unit, onDictate: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(Modifier.size(width = 200.dp, height = 156.dp), contentAlignment = Alignment.Center) {
            PaperCard(noteRaisedTileColor(), c.onSurfaceVariant, -12f, (-38).dp, 10.dp)
            PaperCard(c.primaryContainer, c.onPrimaryContainer, 12f, 38.dp, 10.dp)
            PaperCard(c.primary, c.onPrimary, 0f, 0.dp, 0.dp, front = true)
        }
        Text("No notes yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = c.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            NoteTile(
                onClick = onWrite,
                modifier = Modifier.height(50.dp),
                shape = joinStart(25.dp, JoinEdge),
                container = c.primary,
                content = c.onPrimary,
            ) {
                Row(
                    Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.Add, null, modifier = Modifier.size(20.dp))
                    Text("Write", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            NoteTile(
                onClick = onDictate,
                modifier = Modifier.height(50.dp),
                shape = joinEnd(25.dp, JoinEdge),
                container = noteSurfaceTileColor(),
                content = c.onSurface,
            ) {
                Row(
                    Modifier.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.Mic, null, modifier = Modifier.size(19.dp))
                    Text("Dictate", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun PaperCard(color: androidx.compose.ui.graphics.Color, ink: androidx.compose.ui.graphics.Color, tilt: Float, x: androidx.compose.ui.unit.Dp, y: androidx.compose.ui.unit.Dp, front: Boolean = false) {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier
            .offset(x = x, y = y)
            .graphicsLayer { rotationZ = tilt }
            .size(width = if (front) 100.dp else 84.dp, height = if (front) 128.dp else 108.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(color)
            .padding(16.dp),
    ) {
        if (front) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.fillMaxWidth(0.58f).height(6.dp).clip(CircleShape).background(ink.copy(alpha = 0.48f)))
                Box(Modifier.fillMaxWidth(0.84f).height(6.dp).clip(CircleShape).background(ink.copy(alpha = 0.30f)))
                Box(Modifier.fillMaxWidth(0.46f).height(6.dp).clip(CircleShape).background(ink.copy(alpha = 0.30f)))
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(c.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Add, null, tint = c.onSurface, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ToneChip(label: String, selected: Boolean, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(18.dp), onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    NoteTile(
        onClick = onClick,
        modifier = Modifier.height(36.dp),
        shape = shape,
        container = if (selected) c.primary else noteSurfaceTileColor(),
        content = if (selected) c.onPrimary else c.onSurface,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 15.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun FilterRow(ui: NotesUi, vm: NotesViewModel, onClearFocus: () -> Unit = {}) {
    val filters = NoteFilter.entries
    val total = filters.size + ui.tags.size
    Row(
        Modifier.fillMaxWidth().nebHorizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        filters.forEachIndexed { i, f ->
            val label = if (f == NoteFilter.Trash && ui.binCount > 0) "${f.label} ${ui.binCount}" else f.label
            ToneChip(label, ui.filter == f, rowItemShape(i, total, outer = 18.dp, inner = JoinEdge)) { onClearFocus(); vm.setFilter(f) }
        }
        ui.tags.forEachIndexed { i, t ->
            ToneChip("#$t", ui.tag == t, rowItemShape(filters.size + i, total, outer = 18.dp, inner = JoinEdge)) { onClearFocus(); vm.setTag(t) }
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int, inBin: Boolean, inArchive: Boolean,
    onClose: () -> Unit, onAll: () -> Unit, onPin: () -> Unit, onTone: () -> Unit,
    onArchive: () -> Unit, onTrash: () -> Unit, onRestore: () -> Unit, onDeleteForever: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Cancel selection") }
        Text("$count", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        IconButton(onClick = onAll) { Icon(Icons.Outlined.SelectAll, "Select all") }
        if (inBin) {
            IconButton(onClick = onRestore) { Icon(Icons.Outlined.RestoreFromTrash, "Restore") }
            IconButton(onClick = onDeleteForever) { Icon(Icons.Outlined.DeleteForever, "Delete forever", tint = MaterialTheme.colorScheme.error) }
        } else {
            if (!inArchive) IconButton(onClick = onPin) { Icon(Icons.Outlined.PushPin, "Pin") }
            IconButton(onClick = onTone) { Icon(Icons.Outlined.Palette, "Colour") }
            IconButton(onClick = onArchive) { Icon(if (inArchive) Icons.Outlined.Unarchive else Icons.Outlined.Archive, if (inArchive) "Unarchive" else "Archive") }
            IconButton(onClick = onTrash) { Icon(Icons.Outlined.Delete, "Move to bin") }
        }
    }
}

@Composable
internal fun ToneSwatches(current: Int, onPick: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        (0 until NoteTones.count).chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { i ->
                    val tone = noteTone(i)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(if (current == i) 26.dp else 18.dp)).background(tone.container)
                                .then(if (tone.hairline || current == i) Modifier.androidBorder(current == i) else Modifier)
                                .combinedClickable(onClick = { onPick(i) }),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (current == i) Icon(Icons.Outlined.Check, null, tint = tone.content)
                        }
                        Text(NoteTones.labels[i], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Modifier.androidBorder(strong: Boolean): Modifier {
    val c = MaterialTheme.colorScheme
    return this.then(Modifier.border(if (strong) 2.dp else 1.dp, if (strong) c.primary else c.outlineVariant, RoundedCornerShape(18.dp)))
}
