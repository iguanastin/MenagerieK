package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.app.menagerie.model.SimilarPair

class DatabaseDeleteNonDupe(private val item1ID: Int, private val item2ID: Int): DatabaseUpdate() {

    constructor(item1: Item, item2: Item): this(item1.id, item2.id)
    constructor(pair: SimilarPair<Item>): this(pair.obj1, pair.obj2)


    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getPrepared("DatabaseDeleteNonDupe", "DELETE FROM non_dupes WHERE (item_1=? AND item_2=?) OR (item_2=? AND item_1=?);")

        ps.setInt(1, item1ID)
        ps.setInt(2, item2ID)

        ps.setInt(3, item1ID)
        ps.setInt(4, item2ID)

        return ps.executeUpdate()
    }

    override fun toString(): String {
        return "DeleteNonDupe(item1=$item1ID, item2=$item2ID)"
    }

}