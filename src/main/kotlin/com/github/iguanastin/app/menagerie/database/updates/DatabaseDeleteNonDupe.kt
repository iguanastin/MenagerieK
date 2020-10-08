package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.Item
import com.github.iguanastin.app.menagerie.database.MenagerieDatabase

class DatabaseDeleteNonDupe(private val item1ID: Int, private val item2ID: Int): DatabaseUpdate() {

    constructor(item1: Item, item2: Item): this(item1.id, item2.id)
    constructor(pair: Pair<Item, Item>): this(pair.first, pair.second)


    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getPrepared("DatabaseDeleteNonDupe", "DELETE FROM non_dupes WHERE item_1=? AND item_2=?;")

        ps.setInt(1, item1ID)
        ps.setInt(2, item2ID)

        return ps.executeUpdate()
    }

}