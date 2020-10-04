package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.Item
import com.github.iguanastin.app.menagerie.Tag
import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.database.MenagerieDatabaseException
import java.sql.Connection
import java.sql.PreparedStatement

class DatabaseCreateNonDupe(private val item1ID: Int, private val item2ID: Int): DatabaseUpdate() {

    constructor(item1: Item, item2: Item): this(item1.id, item2.id)
    constructor(pair: Pair<Item, Item>): this(pair.first, pair.second)


    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getCachedStatement(this, "default")

        ps.setInt(1, item1ID)
        ps.setInt(2, item2ID)

        return ps.executeUpdate()
    }

    override fun prepareStatement(conn: Connection, key: String): PreparedStatement {
        when (key) {
            "default" -> return conn.prepareStatement("INSERT INTO non_dupes(item_1, item_2) VALUES (?, ?);")
            else -> throw MenagerieDatabaseException("Invalid statement key: $key")
        }
    }

}