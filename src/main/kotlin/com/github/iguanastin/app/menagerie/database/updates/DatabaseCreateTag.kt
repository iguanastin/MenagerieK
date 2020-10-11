package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.model.Tag
import com.github.iguanastin.app.menagerie.database.MenagerieDatabase

class DatabaseCreateTag(private val id: Int, private val name: String, private val color: String?): DatabaseUpdate() {

    constructor(tag: Tag): this(tag.id, tag.name, tag.color)


    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getPrepared("DatabaseCreateTag", "INSERT INTO tags(id, name, color) VALUES (?, ?, ?);")

        ps.setInt(1, id)
        ps.setNString(2, name)
        ps.setNString(3, color)

        return ps.executeUpdate()
    }

}