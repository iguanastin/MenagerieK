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
        val ps = db.getCachedStatement(this, "default")

        ps.setInt(1, id)
        ps.setLong(2, added)
        ps.setInt(3, id)
        ps.setNString(4, md5)
        ps.setNString(5, file.absolutePath)
        ps.setInt(6, id)
        ps.setBoolean(7, noSimilar)
        ps.setBinaryStream(8, histogram?.alphaToInputStream())
        ps.setBinaryStream(9, histogram?.redToInputStream())
        ps.setBinaryStream(10, histogram?.greenToInputStream())
        ps.setBinaryStream(11, histogram?.blueToInputStream())

        return ps.executeUpdate()
    }

    override fun prepareStatement(conn: Connection, key: String): PreparedStatement {
        when (key) {
            "default" -> return conn.prepareStatement("INSERT INTO items(id, added) VALUES (?, ?);" +
                    "INSERT INTO files(id, md5, file) VALUES (?, ?, ?);" +
                    "INSERT INTO images(id, no_similar, hist_a, hist_r, hist_g, hist_b) VALUES (?, ?, ?, ?, ?, ?);")
            else -> throw MenagerieDatabaseException("Invalid statement key: $key")
        }
    }

}