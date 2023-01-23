package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.app.menagerie.model.Tag

class DatabaseTagItem(private val itemID: Int, private val tagID: Int): DatabaseUpdate() {

    constructor(item: Item, tag: Tag): this(item.id, tag.id)


    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getPrepared("DatabaseTagItem", "INSERT INTO tagged(item_id, tag_id) VALUES (?, ?);")

        ps.setInt(1, itemID)
        ps.setInt(2, tagID)

        return ps.executeUpdate()
    }

    override fun toString(): String {
        return "TagItem(item=$itemID, tag=$tagID)"
    }

}