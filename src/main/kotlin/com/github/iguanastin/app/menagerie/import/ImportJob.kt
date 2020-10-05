package com.github.iguanastin.app.menagerie.import

import com.github.iguanastin.app.menagerie.*
import com.github.iguanastin.view.image
import java.io.File

open class ImportJob(val file: File) {

    var item: Item? = null
        private set


    open fun import(menagerie: Menagerie): Item {
        val id = menagerie.takeNextItemID()
        val added = System.currentTimeMillis()
        val md5 = FileItem.fileHash(file)

        item = if (ImageItem.isImage(file)) {
            val histogram = Histogram(image(file))
            ImageItem(id, added, md5, file, noSimilar = false, histogram = histogram)
        } else {
            FileItem(id, added, md5, file)
        }

        menagerie.addItem(item!!)

        return item!!
    }

}