package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.model.GroupItem

class DatabaseCreateGroup(item: GroupItem): DatabaseCreateItem(item) {

    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getPrepared("DatabaseCreateGroup", "INSERT INTO groups(id, title, items) VALUES(?, ?, ?);")

        item as GroupItem
        ps.setInt(1, item.id)
        ps.setNString(2, item.title)
        ps.setNClob(3, item.items.joinToString(separator = ",").reader())

        return super.sync(db) + ps.executeUpdate()
    }

}