package com.github.iguanastin.app.menagerie.model

import javafx.scene.image.Image
import java.io.File

class VideoItem(id: Int, added: Long, menagerie: Menagerie, md5: String, file: File): FileItem(id, added, menagerie, md5, file) {

    companion object {
        val fileExtensions = listOf("webm", "mp4", "mov", "flv", "avi", "wmv", "3gp", "mpg", "m4v", "mkv")

        fun isVideo(file: File): Boolean {
            return fileExtensions.contains(file.extension.lowercase())
        }
    }

    override fun loadThumbnail(): Image {
        return super.loadThumbnail() // TODO generate video thumbnail
    }

    override fun replace(with: Item, replaceTags: Boolean): Boolean {
        if (with !is VideoItem) return false
        return super.replace(with, replaceTags)
    }

}