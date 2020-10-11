package com.github.iguanastin.app.menagerie.import

import com.github.iguanastin.app.menagerie.model.*
import com.github.iguanastin.view.image
import mu.KotlinLogging
import java.io.File

private val log = KotlinLogging.logger {}
open class ImportJob(val file: File, var onStart: ((ImportJob) -> Unit)? = null, var onFinish: ((FileItem) -> Unit)? = null) {

    var item: FileItem? = null
        private set


    open fun import(menagerie: Menagerie): FileItem {
        onStart?.invoke(this)

        log.debug { "Importing \"$file\"" }

        if (menagerie.hasFile(file)) throw MenagerieException("File already present in Menagerie: $file")

        val id = menagerie.reserveItemID()
        val added = System.currentTimeMillis()
        val md5 = FileItem.fileHash(file)

        item = when {
            ImageItem.isImage(file) -> {
                val histogram = Histogram.from(image(file))
                ImageItem(id, added, menagerie, md5, file, noSimilar = false, histogram = histogram)
            }
            VideoItem.isVideo(file) -> {
                VideoItem(id, added, menagerie, md5, file)
            }
            else -> {
                FileItem(id, added, menagerie, md5, file)
            }
        }

        menagerie.addItem(item!!)

        log.debug { "Imported \"$file\" with: id=$id, added=$added, md5=$md5" }

        onFinish?.invoke(item!!)

        return item!!
    }

    open fun cleanupAfterError(e: Exception) {}

}