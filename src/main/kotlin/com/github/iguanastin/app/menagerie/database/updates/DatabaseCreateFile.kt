package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.model.FileItem
import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import java.io.File

open class DatabaseCreateFile(id: Int, added: Long, private val md5: String?, private val file: File): DatabaseCreateItem(id, added) {

    constructor(item: FileItem): this(item.id, item.added, item.md5, item.file)


    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getPrepared("DatabaseCreateFile", "INSERT INTO files(id, md5, file) VALUES (?, ?, ?);")
        ps.setInt(1, id)
        ps.setNString(2, md5)
        ps.setNString(3, file.absolutePath)

        return super.sync(db) + ps.executeUpdate()
    }

}