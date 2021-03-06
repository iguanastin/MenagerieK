package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.model.Histogram
import com.github.iguanastin.app.menagerie.model.ImageItem
import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import java.io.File

class OldDatabaseCreateMedia(private val id: Int, private val added: Long, private val md5: String?, private val file: File, private val noSimilar: Boolean, private val histogram: Histogram?): DatabaseUpdate() {

    constructor(item: ImageItem): this(item.id, item.added, item.md5, item.file, item.noSimilar, item.histogram)


    override fun sync(db: MenagerieDatabase): Int {
        val ps1 = db.getPrepared("DatabaseCreateMedia.items", "INSERT INTO items(id, added) VALUES (?, ?);")
        ps1.setInt(1, id)
        ps1.setLong(2, added)

        val ps2 = db.getPrepared("DatabaseCreateMedia.prepared", "INSERT INTO media(id, path, md5, hist_a, hist_r, hist_g, hist_b, no_similar) VALUES (?, ?, ?, ?, ?, ?, ?, ?);")
        ps2.setInt(1, id)
        ps2.setNString(2, file.absolutePath)
        ps2.setNString(3, md5)
        ps2.setBinaryStream(4, histogram?.alphaToInputStream())
        ps2.setBinaryStream(5, histogram?.redToInputStream())
        ps2.setBinaryStream(6, histogram?.greenToInputStream())
        ps2.setBinaryStream(7, histogram?.blueToInputStream())
        ps2.setBoolean(8, noSimilar)

        return ps1.executeUpdate() + ps2.executeUpdate()
    }

}