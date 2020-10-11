package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.model.FileItem
import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import java.io.File

class DatabaseSetFilePath(private val itemID: Int, private val file: String): DatabaseUpdate() {

    constructor(item: FileItem, file: File = item.file): this(item.id, file.absolutePath)


    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getPrepared("DatabaseSetFilePath", "UPDATE files SET file=? WHERE id=?;")

        ps.setNString(1, file)
        ps.setInt(2, itemID)

        return ps.executeUpdate()
    }

}