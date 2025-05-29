package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.import.Import

class DatabaseDeleteImport(val id: Int): DatabaseUpdate() {

    constructor(import: Import): this(import.id)

    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getPrepared("DatabaseDeleteImport", "DELETE FROM imports WHERE id=?;")

        ps.setInt(1, id)

        return ps.executeUpdate()
    }

    override fun toString(): String {
        return "DeleteImport(id=${id})"
    }

}