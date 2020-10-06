package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.FileItem
import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.database.MenagerieDatabaseException
import java.io.File
import java.sql.Connection
import java.sql.PreparedStatement

open class DatabaseCreateFile(id: Int, added: Long, private val md5: String?, private val file: File): DatabaseCreateItem(id, added) {

    constructor(item: FileItem): this(item.id, item.added, item.md5, item.file)


    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getCachedStatement(this, "default")
        ps.setInt(1, id)
        ps.setNString(2, md5)
        ps.setNString(3, file.absolutePath)

        return super.sync(db) + ps.executeUpdate()
    }

    override fun prepareStatement(conn: Connection, key: String): PreparedStatement {
        return when (key) {
            "default" -> conn.prepareStatement("INSERT INTO files(id, md5, file) VALUES (?, ?, ?);")
            else -> throw MenagerieDatabaseException("Invalid statement key: $key")
        }
    }

}