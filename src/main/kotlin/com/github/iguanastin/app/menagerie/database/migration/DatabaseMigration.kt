package com.github.iguanastin.app.menagerie.database.migration

import java.sql.Connection


abstract class DatabaseMigration {

    abstract val fromVersion: Int
    abstract val toVersion: Int

    abstract fun migrate(db: Connection)

}
