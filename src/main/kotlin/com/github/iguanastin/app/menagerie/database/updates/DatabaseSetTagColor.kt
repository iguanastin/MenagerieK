package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.model.Tag

class DatabaseSetTagColor(private val tagId: Int, private val color: String?): DatabaseUpdate() {

    constructor(tag: Tag, color: String? = tag.color): this(tag.id, color)


    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getPrepared("DatabaseSetTagColor", "UPDATE tags SET color=? WHERE id=?;")

        ps.setNString(1, color)
        ps.setInt(2, tagId)

        return ps.executeUpdate()
    }

}