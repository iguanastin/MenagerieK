package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.Item
import com.github.iguanastin.app.menagerie.Tag
import com.github.iguanastin.app.menagerie.database.MenagerieDatabase

class DatabaseUntagItem(private val itemID: Int, private val tagID: Int): DatabaseUpdate() {

    constructor(item: Item, tag: Tag): this(item.id, tag.id)


    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getPrepared("DatabaseUntagItem", "DELETE FROM tagged WHERE item_id=? AND tag_id=?;")

        ps.setInt(1, itemID)
        ps.setInt(2, tagID)

        return ps.executeUpdate()
    }

}