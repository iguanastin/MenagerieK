package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.Item
import com.github.iguanastin.app.menagerie.Tag
import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.database.MenagerieDatabaseException
import java.sql.Connection
import java.sql.PreparedStatement

class DatabaseTagItem(private val itemID: Int, private val tagID: Int): DatabaseUpdate() {

    constructor(item: Item, tag: Tag): this(item.id, tag.id)


    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getCachedStatement(this, "default")

        ps.setInt(1, itemID)
        ps.setInt(2, tagID)

        return ps.executeUpdate()
    }

    override fun prepareStatement(conn: Connection, key: String): PreparedStatement {
        when (key) {
            "default" -> return conn.prepareStatement("INSERT INTO tagged(item_id, tag_id) VALUES (?, ?);")
            else -> throw MenagerieDatabaseException("Invalid statement key: $key")
        }
    }

}