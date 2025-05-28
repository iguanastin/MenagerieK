package com.github.iguanastin.app.menagerie.database.migration

import mu.KotlinLogging
import java.sql.Connection

private val log = KotlinLogging.logger {}
class MigrateDatabase9To10: DatabaseMigration() {

    override val fromVersion: Int = 9
    override val toVersion: Int = 10

    override fun migrate(db: Connection) {
        db.createStatement().use { s ->
            s.executeUpdate("CREATE TABLE similar(id1 INT NOT NULL, id2 INT NOT NULL, similarity DOUBLE PRECISION NOT NULL, FOREIGN KEY (id1) REFERENCES items(id) ON DELETE CASCADE, FOREIGN KEY (id2) REFERENCES items(id) ON DELETE CASCADE);")

            // Add migration time to version table
            s.executeUpdate("ALTER TABLE version ADD COLUMN time TIMESTAMP WITH TIME ZONE;")
            s.executeUpdate("ALTER TABLE version ALTER COLUMN time SET DEFAULT CURRENT_TIMESTAMP;")
        }
    }


}