package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.model.FileItem

open class DatabaseCreateFile(item: FileItem): DatabaseCreateItem(item) {

    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getPrepared("DatabaseCreateFile", "INSERT INTO files(id, md5, file) VALUES (?, ?, ?);")

        item as FileItem
        ps.setInt(1, item.id)
        ps.setNString(2, item.md5)
        ps.setNString(3, item.file.absolutePath)

        return super.sync(db) + ps.executeUpdate()
    }

    override fun toString(): String {
        return "CreateFileItem(item=${item.id}, md5=${(item as FileItem).md5}, file=${item.file.absolutePath})"
    }

}