package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.Histogram
import com.github.iguanastin.app.menagerie.ImageItem
import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.database.MenagerieDatabaseException
import java.io.File
import java.sql.Connection
import java.sql.PreparedStatement

class DatabaseCreateImage(private val id: Int, private val added: Long, private val md5: String?, private val file: File, private val noSimilar: Boolean, private val histogram: Histogram?): DatabaseUpdate() {

    constructor(item: ImageItem): this(item.id, item.added, item.md5, item.file, item.noSimilar, item.histogram)


    override fun sync(db: MenagerieDatabase): Int {
//        val ps1 = db.getCachedStatement(this, "items")
//        ps1.setInt(1, id)
//        ps1.setLong(2, added)
//
//        val ps2 = db.getCachedStatement(this, "files")
//        ps2.setInt(1, id)
//        ps2.setNString(2, md5)
//        ps2.setNString(3, file.absolutePath)
//
//        val ps3 = db.getCachedStatement(this, "media")
//        ps3.setInt(1, id)
//        ps3.setBoolean(2, noSimilar)
//        ps3.setBinaryStream(3, histogram?.alphaToInputStream())
//        ps3.setBinaryStream(4, histogram?.redToInputStream())
//        ps3.setBinaryStream(5, histogram?.greenToInputStream())
//        ps3.setBinaryStream(6, histogram?.blueToInputStream())
//
//        return ps1.executeUpdate()

        val ps1 = db.getCachedStatement(this, "items")
        ps1.setInt(1, id)
        ps1.setLong(2, added)

        val ps2 = db.getCachedStatement(this, "media")
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

    override fun prepareStatement(conn: Connection, key: String): PreparedStatement {
        return when (key) {
//            "items" -> conn.prepareStatement("INSERT INTO items(id, added) VALUES (?, ?);")
//            "files" -> conn.prepareStatement("INSERT INTO files(id, md5, file) VALUES (?, ?, ?);")
//            "media" -> conn.prepareStatement("INSERT INTO images(id, no_similar, hist_a, hist_r, hist_g, hist_b) VALUES (?, ?, ?, ?, ?, ?);")
            "items" -> conn.prepareStatement("INSERT INTO items(id, added) VALUES (?, ?);")
            "media" -> conn.prepareStatement("INSERT INTO media(id, path, md5, hist_a, hist_r, hist_g, hist_b, no_similar) VALUES (?, ?, ?, ?, ?, ?, ?, ?);")
            else -> throw MenagerieDatabaseException("Invalid statement key: $key")
        }
    }

}