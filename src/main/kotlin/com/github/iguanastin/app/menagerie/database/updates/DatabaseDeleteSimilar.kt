package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.app.menagerie.model.SimilarPair

class DatabaseDeleteSimilar(val id1: Int, val id2: Int): DatabaseUpdate() {

    constructor(i1: Item, i2: Item): this(i1.id, i2.id)
    constructor(pair: SimilarPair<Item>): this(pair.obj1.id, pair.obj2.id)

    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getPrepared("DatabaseDeleteSimilar", "DELETE FROM similar WHERE id1=? AND id2=?;")

        ps.setInt(1, id1)
        ps.setInt(2, id2)

        return ps.executeUpdate()
    }

    override fun toString(): String {
        return "DeleteSimilar(id1=${id1}, id2=${id2})"
    }
}