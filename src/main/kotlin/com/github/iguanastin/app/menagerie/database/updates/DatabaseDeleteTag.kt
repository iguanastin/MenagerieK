package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.Tag
import com.github.iguanastin.app.menagerie.database.MenagerieDatabase

class DatabaseDeleteTag(private val id: Int): DatabaseUpdate() {


    constructor(tag: Tag): this(tag.id)


    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getPrepared("DatabaseDeleteTag", "DELETE FROM tags WHERE id=?;")

        ps.setInt(1, id)

        return ps.executeUpdate()
    }

}