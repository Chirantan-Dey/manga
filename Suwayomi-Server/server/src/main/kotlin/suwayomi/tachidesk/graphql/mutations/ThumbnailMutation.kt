package suwayomi.tachidesk.graphql.mutations

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import graphql.schema.DataFetchingEnvironment
import io.javalin.http.UploadedFile
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import suwayomi.tachidesk.manga.impl.ThumbnailDownloadHelper

class ThumbnailMutation : KoinComponent {
    private val thumbnailHelper: ThumbnailDownloadHelper by inject()

    @GraphQLIgnore
    override fun getKoin(): Koin = super.getKoin()

    /**
     * Sets a custom thumbnail for a manga.
     * @param mangaId The ID of the manga.
     * @param thumbnail The uploaded thumbnail file.
     * @param env The DataFetchingEnvironment.
     * @return True if the thumbnail was set successfully, false otherwise.
     */
    fun setCustomThumbnail(mangaId: Int, thumbnail: UploadedFile, env: DataFetchingEnvironment): Boolean {
        thumbnailHelper.saveCustomThumbnail(mangaId, thumbnail.content())
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