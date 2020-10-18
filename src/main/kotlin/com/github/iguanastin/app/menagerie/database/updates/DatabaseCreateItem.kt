package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.model.Item

open class DatabaseCreateItem(val item: Item): DatabaseUpdate() {

    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getPrepared("DatabaseCreateItem", "INSERT INTO items(id, added) VALUES (?, ?);")

        ps.setInt(1, item.id)
        ps.setLong(2, item.added)

        var updates = ps.executeUpdate()

        for (tag in item.tags) {
            updates += DatabaseTagItem(item, tag).sync(db)
        }

        return updates
    }

}