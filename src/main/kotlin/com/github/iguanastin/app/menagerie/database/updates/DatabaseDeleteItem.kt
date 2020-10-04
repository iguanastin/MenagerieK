package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.Item
import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.database.MenagerieDatabaseException
import java.sql.Connection
import java.sql.PreparedStatement

class DatabaseDeleteItem(private val id: Int): DatabaseUpdate() {


    constructor(item: Item): this(item.id)


    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getCachedStatement(this, "default")

        ps.setInt(1, id)

        return ps.executeUpdate()
    }

    override fun prepareStatement(conn: Connection, key: String): PreparedStatement {
        when (key) {
            "default" -> return conn.prepareStatement("DROP FROM items WHERE id=?;")
            else -> throw MenagerieDatabaseException("Invalid statement key: $key")
        }
    }

}