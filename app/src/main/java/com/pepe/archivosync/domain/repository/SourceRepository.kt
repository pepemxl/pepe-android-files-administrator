package com.pepe.archivosync.domain.repository

import com.pepe.archivosync.domain.model.FileNode
import com.pepe.archivosync.domain.model.StorageVolume
import java.io.InputStream

/**
 * Reads the device's browsable storage. Implemented over the Storage Access
 * Framework (DocumentFile) so it stays Scoped-Storage compliant and can also
 * target USB-OTG / NAS folders the user grants.
 */
interface SourceRepository {
    /** Root node for a volume, or null if the user hasn't granted access yet. */
    suspend fun root(volume: StorageVolume): FileNode?

    /** Direct children of [parentId] (a document URI string). */
    suspend fun children(parentId: String): List<FileNode>

    /** Opens a readable stream for a file node; caller must close it. */
    suspend fun openInput(nodeId: String): InputStream?

    /** Persist a user-granted tree URI for [volume] (from OpenDocumentTree). */
    suspend fun grantTree(volume: StorageVolume, treeUri: String)
}
