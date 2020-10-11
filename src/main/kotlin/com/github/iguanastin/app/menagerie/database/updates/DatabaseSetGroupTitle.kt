package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.GroupItem
import com.github.iguanastin.app.menagerie.database.MenagerieDatabase

class DatabaseSetGroupTitle(private val itemID: Int, private val title: String): DatabaseUpdate() {

    constructor(item: GroupItem, title: String = item.title): this(item.id, title)


    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getPrepared("DatabaseSetGroupItems", "UPDATE groups SET title=? WHERE id=?;")

        ps.setNString(1, title)
        ps.setInt(2, itemID)

        return ps.executeUpdate()
    }

}