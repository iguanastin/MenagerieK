package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.Item
import com.github.iguanastin.app.menagerie.database.MenagerieDatabase

open class DatabaseCreateItem(val id: Int, val added: Long): DatabaseUpdate() {

    constructor(item: Item): this(item.id, item.added)


    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getPrepared("DatabaseCreateItem", "INSERT INTO items(id, added) VALUES (?, ?);")

        ps.setInt(1, id)
        ps.setLong(2, added)

        return ps.executeUpdate()
    }

}