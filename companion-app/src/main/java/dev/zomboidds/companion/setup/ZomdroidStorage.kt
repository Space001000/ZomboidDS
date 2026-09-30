package dev.zomboidds.companion.setup

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document

/**
 * Zomdroid's files, reached through the folder permission the user granted in the system picker
 * (Zomdroid exposes its storage as a DocumentsProvider).
 *
 * Layout: `instances/<instance name>/Zomboid/mods/<mod folders>`.
 */
class ZomdroidStorage(private val resolver: ContentResolver, val tree: Uri) {

    data class Entry(val id: String, val name: String, val isDirectory: Boolean)

    val rootId: String = DocumentsContract.getTreeDocumentId(tree)

    fun children(parentId: String): List<Entry> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
        val result = mutableListOf<Entry>()
        resolver.query(uri, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                result += Entry(c.getString(0), c.getString(1), c.getString(2) == Document.MIME_TYPE_DIR)
            }
        }
        return result
    }

    fun child(parentId: String, name: String): Entry? = children(parentId).firstOrNull { it.name == name }

    /** Game instances by name (Zomdroid supports several, e.g. different builds). */
    fun instances(): List<Entry> =
        child(rootId, "instances")?.let { dir -> children(dir.id).filter { it.isDirectory } }.orEmpty()

    fun modsDir(instance: Entry): Entry? =
        child(instance.id, "Zomboid")?.let { child(it.id, "mods") }

    fun uri(documentId: String): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, documentId)

    /** Follows [names] down from [parentId], e.g. `path(instance.id, "game", "media")`. */
    fun path(parentId: String, vararg names: String): Entry? =
        names.fold<String, Entry?>(Entry(parentId, "", true)) { dir, name -> dir?.let { child(it.id, name) } }

    fun readBytes(documentId: String): ByteArray =
        resolver.openInputStream(uri(documentId))!!.use { it.readBytes() }

    fun readText(documentId: String): String = readBytes(documentId).decodeToString()

    fun writeText(documentId: String, text: String) {
        resolver.openOutputStream(uri(documentId), "wt")!!.use { it.write(text.encodeToByteArray()) }
    }

    fun createDirectory(parentId: String, name: String): String = create(parentId, Document.MIME_TYPE_DIR, name)

    fun createFile(parentId: String, name: String): String = create(parentId, "application/octet-stream", name)

    fun delete(documentId: String) {
        DocumentsContract.deleteDocument(resolver, uri(documentId))
    }

    fun rename(documentId: String, newName: String) {
        DocumentsContract.renameDocument(resolver, uri(documentId), newName)
            ?: error("could not rename $documentId to $newName")
    }

    companion object {
        const val PACKAGE = "com.zomdroid"
        const val AUTHORITY = "com.zomdroid.STORAGE_PROVIDER_AUTHORITY"

        /** Zomdroid's document ids are file paths; this is its top-level folder. */
        private const val ROOT_DOCUMENT_ID = "/data/data/com.zomdroid/files"

        /** Where the folder picker should open, so the user only has to confirm. */
        val pickerStartUri: Uri = DocumentsContract.buildDocumentUri(AUTHORITY, ROOT_DOCUMENT_ID)

        /** The granted Zomdroid folder, if any. */
        fun find(resolver: ContentResolver): ZomdroidStorage? =
            resolver.persistedUriPermissions
                .firstOrNull { it.uri.authority == AUTHORITY && it.isReadPermission && it.isWritePermission }
                ?.let { ZomdroidStorage(resolver, it.uri) }
    }

    private fun create(parentId: String, mimeType: String, name: String): String {
        val created = DocumentsContract.createDocument(resolver, uri(parentId), mimeType, name)
            ?: error("could not create $name")
        val id = DocumentsContract.getDocumentId(created)
        // Zomdroid appends " (2)" instead of failing when the name exists; we never want that.
        check(id.substringAfterLast('/') == name) { "'$name' already exists in $parentId" }
        return id
    }
}
