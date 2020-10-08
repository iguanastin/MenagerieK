package com.github.iguanastin.app.menagerie

import javafx.scene.image.Image
import java.io.File

class ImageItem(id: Int, added: Long, menagerie: Menagerie, md5: String, file: File, var noSimilar: Boolean = false, var histogram: Histogram? = null): FileItem(id, added, menagerie, md5, file) {

    companion object {
        val fileExtensions = listOf("png", "jpg", "jpeg", "gif", "bmp")

        fun isImage(file: File): Boolean {
            return fileExtensions.contains(file.extension.toLowerCase())
        }
    }

    override fun loadThumbnail(): Image {
        return Image(file.toURI().toString(), thumbnailSize, thumbnailSize, true, true, true)
    }

    override fun similarityTo(other: Item): Double {
        if (other !is ImageItem || histogram == null || other.histogram == null) return super.similarityTo(other)

        return histogram!!.similarityTo(other.histogram!!)
    }

}