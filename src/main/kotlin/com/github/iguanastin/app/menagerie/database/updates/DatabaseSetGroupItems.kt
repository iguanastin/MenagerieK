package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.model.FileItem
import com.github.iguanastin.app.menagerie.model.GroupItem

class DatabaseSetGroupItems(private val itemID: Int, private val ids: List<Int>): DatabaseUpdate() {

    constructor(item: GroupItem, items: List<FileItem> = item.items): this(item.id, items.map { it.id })


    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getPrepared("DatabaseSetGroupItems", "UPDATE groups SET items=? WHERE id=?;")

        ps.setNClob(1, ids.joinToString(",").reader())
        ps.setInt(2, itemID)

        return ps.executeUpdate()
    }

    override fun toString(): String {
        return "SetGroupItems(item=$itemID, itemCount=${ids.size})"
    }

}