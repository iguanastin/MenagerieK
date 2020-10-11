package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.model.Histogram
import com.github.iguanastin.app.menagerie.model.ImageItem
import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import java.io.File

class DatabaseCreateImage(id: Int, added: Long, md5: String, file: File, val noSimilar: Boolean, val histogram: Histogram?): DatabaseCreateFile(id, added, md5, file) {

    constructor(item: ImageItem): this(item.id, item.added, item.md5, item.file, item.noSimilar, item.histogram)


    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getPrepared("DatabaseCreateImage", "INSERT INTO images(id, no_similar, hist_a, hist_r, hist_g, hist_b) VALUES (?, ?, ?, ?, ?, ?);")

        ps.setInt(1, id)
        ps.setBoolean(2, noSimilar)
        ps.setBinaryStream(3, histogram?.alphaToInputStream())
        ps.setBinaryStream(4, histogram?.redToInputStream())
        ps.setBinaryStream(5, histogram?.greenToInputStream())
        ps.setBinaryStream(6, histogram?.blueToInputStream())

        return super.sync(db) + ps.executeUpdate()
    }

}