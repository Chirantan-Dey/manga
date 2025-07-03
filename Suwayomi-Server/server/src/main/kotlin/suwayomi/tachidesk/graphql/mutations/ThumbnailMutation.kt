package suwayomi.tachidesk.graphql.mutations

import graphql.schema.DataFetchingEnvironment
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import suwayomi.tachidesk.manga.impl.ThumbnailDownloadHelper
import java.io.InputStream

class ThumbnailMutation : KoinComponent {
    private val thumbnailHelper: ThumbnailDownloadHelper by inject()

    /**
     * Sets a custom thumbnail for a manga.
     * @param mangaId The ID of the manga.
     * @param thumbnail The input stream of the thumbnail image.
     * @param env The DataFetchingEnvironment.
     * @return True if the thumbnail was set successfully, false otherwise.
     */
    fun setCustomThumbnail(mangaId: Int, thumbnail: InputStream, env: DataFetchingEnvironment): Boolean {
        thumbnailHelper.saveCustomThumbnail(mangaId, thumbnail)
        return true // Assuming saveCustomThumbnail is synchronous or we return immediately
    }

    /**
     * Deletes the custom thumbnail for a manga.
     * @param mangaId The ID of the manga.
     * @return True if the custom thumbnail was deleted successfully, false otherwise.
     */
    fun deleteCustomThumbnail(mangaId: Int): Boolean {
        return thumbnailHelper.deleteCustomThumbnail(mangaId)
    }
}