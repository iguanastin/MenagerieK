package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.Item
import com.github.iguanastin.app.menagerie.Tag
import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.database.MenagerieDatabaseException
import java.sql.Connection
import java.sql.PreparedStatement

class DatabaseCreateTag(private val id: Int, private val name: String, private val color: String?): DatabaseUpdate() {

    constructor(tag: Tag): this(tag.id, tag.name, tag.color)


    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getCachedStatement(this, "default")

        ps.setInt(1, id)
        ps.setNString(2, name)
        ps.setNString(3, color)

        return ps.executeUpdate()
    }

    override fun prepareStatement(conn: Connection, key: String): PreparedStatement {
        when (key) {
            "default" -> return conn.prepareStatement("INSERT INTO tags(id, name, color) VALUES (?, ?, ?);")
            else -> throw MenagerieDatabaseException("Invalid statement key: $key")
        }
    }

}