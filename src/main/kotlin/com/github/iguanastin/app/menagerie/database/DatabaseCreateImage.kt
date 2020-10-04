package com.github.iguanastin.app.menagerie.database

import com.github.iguanastin.app.menagerie.Histogram
import com.github.iguanastin.app.menagerie.ImageItem
import java.io.File
import java.sql.Statement

class DatabaseCreateImage(id: Int, added: Long, md5: String?, file: File, noSimilar: Boolean, histogram: Histogram?) {

    private val statements = listOf(
            "INSERT INTO items(id, added) VALUES ($id, $added);",
            "INSERT INTO files(id, md5, file) VALUES ($id, $md5, ${file.absolutePath});",
            "INSERT INTO images(id, no_similar, hist_a, hist_r, hist_g, hist_b) VALUES ($id, $noSimilar, ${histogram?.alphaToInputStream()}, ${histogram?.redToInputStream()}, ${histogram?.greenToInputStream()}, ${histogram?.blueToInputStream()});"
    )

    constructor(item: ImageItem): this(item.id, item.added, item.md5, item.file, item.noSimilar, item.histogram)


    fun batch(s: Statement) {
        statements.forEach { s.addBatch(it) }
    }

    fun sync(s: Statement): Int {
        var count = 0

        for (statement in statements) {
            count += s.executeUpdate(statement)
        }

        return count
    }

}