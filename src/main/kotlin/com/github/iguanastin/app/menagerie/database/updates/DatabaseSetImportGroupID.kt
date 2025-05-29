@file:Suppress("unused")

package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.database.MenagerieDatabase

class DatabaseSetImportGroupID(val old: Int, val new: Int): DatabaseUpdate() {


    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getPrepared("DatabaseSetImportGroupID", "UPDATE imports SET group_id=? WHERE group_id=?;")

        ps.setInt(1, new)
        ps.setInt(2, old)

        return ps.executeUpdate()
    }

    override fun toString(): String {
        return "SetImportGroupID(old=$old, new=$new)"
    }

}