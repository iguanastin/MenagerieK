package com.github.iguanastin.app.menagerie.database.migration

import mu.KotlinLogging
import java.sql.Connection

private val log = KotlinLogging.logger {}
class MigrateDatabase10To11: DatabaseMigration() {

    override val fromVersion: Int = 10
    override val toVersion: Int = 11

    override fun migrate(db: Connection) {
        db.createStatement().use { s ->
            s.executeUpdate("CREATE TABLE imports(id INT NOT NULL, url NVARCHAR(1024), file NVARCHAR(1024), group_id INT, group_title NVARCHAR(1024), tags NCLOB);")
        }
    }

}