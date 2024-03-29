package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.model.ImageItem

class DatabaseSetImageNoSimilar(private val itemID: Int, private val noSimilar: Boolean): DatabaseUpdate() {

    constructor(item: ImageItem, noSimilar: Boolean = item.noSimilar): this(item.id, noSimilar)


    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getPrepared("DatabaseSetImageNoSimilar", "UPDATE images SET no_similar=? WHERE id=?;")

        ps.setBoolean(1, noSimilar)
        ps.setInt(2, itemID)

        return ps.executeUpdate()
    }

    override fun toString(): String {
        return "SetImageNoSimilar(item=$itemID, value=$noSimilar)"
    }

}