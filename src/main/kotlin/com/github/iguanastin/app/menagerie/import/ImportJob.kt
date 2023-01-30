package com.github.iguanastin.app.menagerie.import

import com.github.iguanastin.app.menagerie.model.*
import com.github.iguanastin.view.image
import mu.KotlinLogging
import java.io.File

private val log = KotlinLogging.logger {}
open class ImportJob(val file: File, val addTags: List<Tag>? = null) {

    var onStart: MutableSet<(ImportJob) -> Unit> = mutableSetOf()
    var onProgress: MutableSet<(String, Double) -> Unit> = mutableSetOf()
    var onError: MutableSet<(Exception) -> Unit> = mutableSetOf()
    var onFinish: MutableSet<(FileItem) -> Unit> = mutableSetOf()

    @Volatile
    var isCancelled: Boolean = false
        private set

    var item: FileItem? = null
        private set


    open fun import(menagerie: Menagerie): FileItem {
        onProgress.forEach { it("Importing file", 0.0) }

        log.debug { "Importing \"$file\"" }

        if (menagerie.hasFile(file)) throw MenagerieException("File already present in Menagerie: $file")

        val id = menagerie.reserveItemID()
        val added = System.currentTimeMillis()
        onProgress.forEach { it("Hashing file", 0.33) }
        val md5 = FileItem.fileHash(file)

        item = when {
            ImageItem.isImage(file) -> {
                createImageItem(menagerie, id, added, md5)
            }
            VideoItem.isVideo(file) -> {
                VideoItem(id, added, menagerie, md5, file)
            }
            else -> {
                FileItem(id, added, menagerie, md5, file)
            }
        }

        initTags(menagerie)

        log.debug { "Generated item for \"$file\" with: id=$id, added=$added, md5=$md5" }

        onProgress.forEach { it("Finished importing", 1.0) }

        return item!!
    }

    private fun createImageItem(
        menagerie: Menagerie,
        id: Int,
        added: Long,
        md5: String
    ): ImageItem {
        onProgress.forEach { it("Creating histogram", 0.66) }
        val histogram = Histogram.from(image(file))

        onProgress.forEach { it("Finding similar items", 0.8) }
        var noSimilar = true
        for (item in menagerie.items) {
            if (histogram == null) break
            if (item is ImageItem) {
                val h2 = item.histogram
                if (h2 != null && histogram.similarityTo(h2) > ImageItem.noSimilarMax) {
                    noSimilar = false
                    item.noSimilar = false
                }
            }
        }

        return ImageItem(id, added, menagerie, md5, file, noSimilar = noSimilar, histogram = histogram)
    }

    private fun initTags(menagerie: Menagerie) {
        item?.addTag(menagerie.getOrMakeTag("tagme"))
        addTags?.forEach { item?.addTag(it) }
    }

    open fun cleanupAfterError(e: Exception) {}

    fun cancel() {
        isCancelled = true
    }

}