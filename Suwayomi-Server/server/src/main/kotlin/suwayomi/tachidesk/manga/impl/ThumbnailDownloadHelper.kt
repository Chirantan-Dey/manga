package suwayomi.tachidesk.manga.impl

import suwayomi.tachidesk.manga.impl.download.fileProvider.impl.CustomThumbnailFileProvider
import suwayomi.tachidesk.manga.impl.download.fileProvider.impl.ThumbnailFileProvider
import java.io.InputStream

object ThumbnailDownloadHelper {
    fun getImage(mangaId: Int): Pair<InputStream, String> {
        return if (hasCustomThumbnail(mangaId)) {
            customProvider(mangaId).getCustomThumbnail()
        } else {
            defaultProvider(mangaId).getImage().execute()
        }
    }

    fun delete(mangaId: Int): Boolean {
        // Delete both default and custom thumbnails
        var deleted = defaultProvider(mangaId).delete()
        if (deleteCustomThumbnail(mangaId)) deleted = true
        return deleted
    }

    suspend fun download(mangaId: Int): Boolean = defaultProvider(mangaId).download().execute()

    // Custom thumbnail operations
    fun saveCustomThumbnail(mangaId: Int, inputStream: InputStream) {
        customProvider(mangaId).saveCustomThumbnail(inputStream)
    }

    fun deleteCustomThumbnail(mangaId: Int): Boolean {
        return try {
            customProvider(mangaId).deleteCustomThumbnail()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun hasCustomThumbnail(mangaId: Int): Boolean {
        return customProvider(mangaId).hasCustomThumbnail()
    }

    // Providers
    private fun defaultProvider(mangaId: Int): ThumbnailFileProvider = ThumbnailFileProvider(mangaId)
    private fun customProvider(mangaId: Int): CustomThumbnailFileProvider = CustomThumbnailFileProvider(mangaId)
}
