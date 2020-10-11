package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.FileItem
import com.github.iguanastin.app.menagerie.GroupItem
import com.github.iguanastin.app.menagerie.database.MenagerieDatabase

class DatabaseSetFileMD5(private val itemID: Int, private val md5: String): DatabaseUpdate() {

    constructor(item: FileItem, md5: String = item.md5): this(item.id, md5)


    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getPrepared("DatabaseSetFileMD5", "UPDATE files SET md5=? WHERE id=?;")

        ps.setNString(1, md5)
        ps.setInt(2, itemID)

        return ps.executeUpdate()
    }

}