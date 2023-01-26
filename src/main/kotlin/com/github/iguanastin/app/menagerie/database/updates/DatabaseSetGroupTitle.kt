@file:Suppress("unused")

package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.database.MenagerieDatabase

class DatabaseSetGroupTitle(private val itemID: Int, private val title: String): DatabaseUpdate() {


    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getPrepared("DatabaseSetGroupTitle", "UPDATE groups SET title=? WHERE id=?;")

        ps.setNString(1, title)
        ps.setInt(2, itemID)

        return ps.executeUpdate()
    }

    override fun toString(): String {
        return "SetGroupTitle(item=$itemID, title=$title)"
    }

}