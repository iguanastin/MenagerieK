package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.model.Item

class DatabaseDeleteItem(private val id: Int): DatabaseUpdate() {


    constructor(item: Item): this(item.id)


    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getPrepared("DatabaseDeleteItem", "DELETE FROM items WHERE id=?;")

        ps.setInt(1, id)

        return ps.executeUpdate()
    }

    override fun toString(): String {
        return "DeleteItem(item=$id)"
    }

}