package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.GroupItem
import com.github.iguanastin.app.menagerie.Item
import com.github.iguanastin.app.menagerie.database.MenagerieDatabase

class DatabaseCreateGroup(id: Int, added: Long, val title: String, val items: List<Item>): DatabaseCreateItem(id, added) {

    constructor(item: GroupItem): this(item.id, item.added, item.title, item.items)


    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getPrepared("DatabaseCreateGroup", "INSERT INTO groups(id, title, items) VALUES(?, ?, ?);")

        ps.setInt(1, id)
        ps.setNString(2, title)
        ps.setNClob(3, items.joinToString(separator = ",").reader())

        return super.sync(db) + ps.executeUpdate()
    }

}