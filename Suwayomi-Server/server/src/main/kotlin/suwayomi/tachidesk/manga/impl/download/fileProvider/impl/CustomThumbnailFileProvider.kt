package suwayomi.tachidesk.manga.impl.download.fileProvider.impl

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.NoSuchFileException // Import NoSuchFileException

class CustomThumbnailFileProvider(private val mangaId: Int) {
    companion object {
        private val customThumbnailDir: Path = Paths.get(
            System.getProperty("user.home"),
            ".suwayomi",
            "custom_thumbnails"
        ).apply {
            if (!Files.exists(this)) Files.createDirectories(this)
        }
    }

    private val customThumbnailPath: Path = customThumbnailDir.resolve("$mangaId.jpg")

    fun saveCustomThumbnail(inputStream: InputStream) {
        Files.copy(inputStream, customThumbnailPath, StandardCopyOption.REPLACE_EXISTING)
    }

    fun deleteCustomThumbnail() {
        if (Files.exists(customThumbnailPath)) {
            Files.delete(customThumbnailPath)
        }
    }

    /**
     * Gets the custom thumbnail as an InputStream.
     * @return A Pair containing the InputStream and the MIME type.
     */
    fun getCustomThumbnail(): Pair<InputStream, String> {
        if (!Files.exists(customThumbnailPath)) {
            throw NoSuchFileException("Custom thumbnail not found for mangaId: $mangaId")
        }
        return Files.newInputStream(customThumbnailPath) to "image/jpeg" // Using 'to' infix function
    }

    fun hasCustomThumbnail(): Boolean = Files.exists(customThumbnailPath)
}