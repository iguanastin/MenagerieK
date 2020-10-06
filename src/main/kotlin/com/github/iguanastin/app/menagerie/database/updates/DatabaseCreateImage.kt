package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.Histogram
import com.github.iguanastin.app.menagerie.ImageItem
import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.database.MenagerieDatabaseException
import java.io.File
import java.sql.Connection
import java.sql.PreparedStatement

class DatabaseCreateImage(id: Int, added: Long, md5: String, file: File, val noSimilar: Boolean, val histogram: Histogram?): DatabaseCreateFile(id, added, md5, file) {

    constructor(item: ImageItem): this(item.id, item.added, item.md5, item.file, item.noSimilar, item.histogram)


    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getCachedStatement(this, "default")
        ps.setInt(1, id)
        ps.setBoolean(2, noSimilar)
        ps.setBinaryStream(3, histogram?.alphaToInputStream())
        ps.setBinaryStream(4, histogram?.redToInputStream())
        ps.setBinaryStream(5, histogram?.greenToInputStream())
        ps.setBinaryStream(6, histogram?.blueToInputStream())

        return super.sync(db) + ps.executeUpdate()
    }

    override fun prepareStatement(conn: Connection, key: String): PreparedStatement {
        return when (key) {
            "default" -> conn.prepareStatement("INSERT INTO images(id, no_similar, hist_a, hist_r, hist_g, hist_b) VALUES (?, ?, ?, ?, ?, ?);")
            else -> throw MenagerieDatabaseException("Invalid statement key: $key")
        }
    }

}