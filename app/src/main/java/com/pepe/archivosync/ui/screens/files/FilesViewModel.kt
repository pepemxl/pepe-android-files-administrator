package com.pepe.archivosync.ui.screens.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pepe.archivosync.domain.model.FileNode
import com.pepe.archivosync.domain.model.StorageVolume
import com.pepe.archivosync.domain.repository.SourceRepository
import com.pepe.archivosync.domain.usecase.BackupSelectionUseCase
import com.pepe.archivosync.domain.usecase.SeedSelectionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FilesUiState(
    val volume: StorageVolume = StorageVolume.INTERNAL,
    val granted: Boolean = false,
    val loading: Boolean = false,
    val crumbs: List<FileNode> = emptyList(),
    val items: List<FileNode> = emptyList(),
    val selected: Map<String, FileNode> = emptyMap(),
) {
    val selectionCount: Int get() = selected.size
    val allVisibleSelected: Boolean
        get() = items.isNotEmpty() && items.all { selected.containsKey(it.id) }
}

@HiltViewModel
class FilesViewModel @Inject constructor(
    private val source: SourceRepository,
    private val backupSelection: BackupSelectionUseCase,
    private val seedSelection: SeedSelectionUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(FilesUiState())
    val state: StateFlow<FilesUiState> = _state.asStateFlow()

    init { selectVolume(StorageVolume.INTERNAL) }

    fun selectVolume(volume: StorageVolume) {
        _state.update { it.copy(volume = volume, loading = true) }
        viewModelScope.launch {
            val root = source.root(volume)
            if (root == null) {
                _state.update { it.copy(granted = false, loading = false, crumbs = emptyList(), items = emptyList()) }
            } else {
                val children = source.children(root.id)
                _state.update {
                    it.copy(granted = true, loading = false, crumbs = listOf(root), items = children)
                }
            }
        }
    }

    fun onTreeGranted(volume: StorageVolume, treeUri: String) {
        viewModelScope.launch {
            source.grantTree(volume, treeUri)
            selectVolume(volume)
        }
    }

    fun open(node: FileNode) {
        if (!node.isDirectory) {
            toggleSelect(node)
            return
        }
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val children = source.children(node.id)
            _state.update { it.copy(loading = false, crumbs = it.crumbs + node, items = children) }
        }
    }

    fun navigateToCrumb(index: Int) {
        val target = _state.value.crumbs.getOrNull(index) ?: return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val children = source.children(target.id)
            _state.update {
                it.copy(loading = false, crumbs = it.crumbs.take(index + 1), items = children)
            }
        }
    }

    fun toggleSelect(node: FileNode) {
        _state.update { st ->
            val next = LinkedHashMap(st.selected)
            if (next.containsKey(node.id)) next.remove(node.id) else next[node.id] = node
            st.copy(selected = next)
        }
    }

    fun selectAllVisible() {
        _state.update { st ->
            val next = LinkedHashMap(st.selected)
            if (st.allVisibleSelected) st.items.forEach { next.remove(it.id) }
            else st.items.forEach { next[it.id] = it }
            st.copy(selected = next)
        }
    }

    fun clearSelection() = _state.update { it.copy(selected = emptyMap()) }

    fun backupSelected(onDone: () -> Unit) {
        val files = _state.value.selected.values.toList()
        viewModelScope.launch {
            backupSelection(files)
            clearSelection()
            onDone()
        }
    }

    fun seedSelected(onDone: () -> Unit) {
        val files = _state.value.selected.values.toList()
        viewModelScope.launch {
            seedSelection(files)
            clearSelection()
            onDone()
        }
    }
}
