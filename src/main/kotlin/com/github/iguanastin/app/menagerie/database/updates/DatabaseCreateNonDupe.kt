package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.app.menagerie.model.SimilarPair

class DatabaseCreateNonDupe(private val item1ID: Int, private val item2ID: Int): DatabaseUpdate() {

    constructor(item1: Item, item2: Item): this(item1.id, item2.id)
    constructor(pair: SimilarPair<Item>): this(pair.obj1, pair.obj2)


    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getPrepared("DatabaseCreateNonDupe", "INSERT INTO non_dupes(item_1, item_2) VALUES (?, ?);")

        ps.setInt(1, item1ID)
        ps.setInt(2, item2ID)

        return ps.executeUpdate()
    }

}