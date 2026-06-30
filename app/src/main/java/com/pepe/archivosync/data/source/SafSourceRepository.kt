package com.pepe.archivosync.data.source

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.documentfile.provider.DocumentFile
import com.pepe.archivosync.domain.model.FileKind
import com.pepe.archivosync.domain.model.FileNode
import com.pepe.archivosync.domain.model.StorageVolume
import com.pepe.archivosync.domain.repository.SourceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Storage Access Framework implementation. The user grants a tree per volume
 * via ACTION_OPEN_DOCUMENT_TREE; we persist the URI permission and walk the
 * tree with DocumentFile so it works for internal storage, SD card and USB-OTG
 * without MANAGE_EXTERNAL_STORAGE.
 */
@Singleton
class SafSourceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) : SourceRepository {

    private fun key(volume: StorageVolume) = when (volume) {
        StorageVolume.INTERNAL -> stringPreferencesKey("tree_internal")
        StorageVolume.SD_CARD -> stringPreferencesKey("tree_sd")
    }

    override suspend fun grantTree(volume: StorageVolume, treeUri: String) {
        val uri = Uri.parse(treeUri)
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        dataStore.edit { it[key(volume)] = treeUri }
    }

    override suspend fun root(volume: StorageVolume): FileNode? = withContext(Dispatchers.IO) {
        val stored = dataStore.data.first()[key(volume)] ?: return@withContext null
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(stored)) ?: return@withContext null
        tree.toNode()
    }

    override suspend fun children(parentId: String): List<FileNode> = withContext(Dispatchers.IO) {
        val parent = DocumentFile.fromTreeUri(context, Uri.parse(parentId)) ?: return@withContext emptyList()
        parent.listFiles()
            .map { it.toNode() }
            .sortedWith(compareByDescending<FileNode> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    override suspend fun openInput(nodeId: String) = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(Uri.parse(nodeId))
    }

    private fun DocumentFile.toNode(): FileNode {
        val displayName = name ?: "—"
        return FileNode(
            id = uri.toString(),
            name = displayName,
            isDirectory = isDirectory,
            sizeBytes = if (isDirectory) 0 else length(),
            childCount = if (isDirectory) listFiles().size else 0,
            lastModified = lastModified(),
            kind = if (isDirectory) FileKind.FOLDER else FileKind.fromName(displayName),
        )
    }
}
