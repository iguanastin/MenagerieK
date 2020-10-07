package com.github.iguanastin.app.menagerie.import

import com.github.iguanastin.app.menagerie.*
import com.github.iguanastin.view.image
import mu.KotlinLogging
import java.io.File

private val log = KotlinLogging.logger {}
open class ImportJob(val file: File, var onStart: ((ImportJob) -> Unit)? = null, var onFinish: ((Item) -> Unit)? = null) {

    var item: Item? = null
        private set


    open fun import(menagerie: Menagerie): Item {
        onStart?.invoke(this)

        log.debug { "Importing \"$file\"" }

        val id = menagerie.takeNextItemID()
        val added = System.currentTimeMillis()
        val md5 = FileItem.fileHash(file)

        item = if (ImageItem.isImage(file)) {
            val histogram = Histogram.from(image(file))
            ImageItem(id, added, md5, file, noSimilar = false, histogram = histogram)
        } else {
            FileItem(id, added, md5, file)
        }

        menagerie.addItem(item!!)

        log.debug { "Imported \"$file\" with: id=$id, added=$added, md5=$md5" }

        onFinish?.invoke(item!!)

        return item!!
    }

}