package suwayomi.tachidesk.manga.impl.download.fileProvider.impl

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

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

    fun hasCustomThumbnail(): Boolean = Files.exists(customThumbnailPath)
}