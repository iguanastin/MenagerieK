package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.Item
import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.database.MenagerieDatabaseException
import java.sql.Connection
import java.sql.PreparedStatement

open class DatabaseCreateItem(val id: Int, val added: Long): DatabaseUpdate() {

    constructor(item: Item): this(item.id, item.added)


    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getCachedStatement(this, "default")

        ps.setInt(1, id)
        ps.setLong(2, added)

        return ps.executeUpdate()
    }

    override fun prepareStatement(conn: Connection, key: String): PreparedStatement {
        return when (key) {
            "default" -> conn.prepareStatement("INSERT INTO items(id, added) VALUES (?, ?);")
            else -> throw MenagerieDatabaseException("Invalid statement key: $key")
        }
    }

}