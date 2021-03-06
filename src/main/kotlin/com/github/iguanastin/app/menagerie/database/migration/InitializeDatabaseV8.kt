package com.github.iguanastin.app.menagerie.database.migration

import mu.KotlinLogging
import java.sql.Connection


private val log = KotlinLogging.logger {}

class InitializeDatabaseV8 : DatabaseMigration() {

    override val fromVersion: Int = -1
    override val toVersion: Int = 8


    override fun migrate(db: Connection) {
        log.info("Migrating database from v$fromVersion to v$toVersion")
        val t = System.currentTimeMillis()

        db.createStatement().use { s ->
            log.info("Dropping any corrupted tables")
            s.executeUpdate("DROP TABLE IF EXISTS imgs; DROP TABLE IF EXISTS tags; DROP TABLE IF EXISTS tagged; DROP TABLE IF EXISTS version; DROP TABLE IF EXISTS items; DROP TABLE IF EXISTS groups; DROP TABLE IF EXISTS media; DROP TABLE IF EXISTS non_dupes; DROP TABLE IF EXISTS tag_notes;")

            log.info("Creating v8 tables")
            s.executeUpdate("CREATE TABLE tags(id INT PRIMARY KEY AUTO_INCREMENT, name NVARCHAR(128) NOT NULL UNIQUE, color NVARCHAR(32));")
            s.executeUpdate("CREATE TABLE tag_notes(tag_id INT, note NVARCHAR(1024), FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE);")
            s.executeUpdate("CREATE TABLE items(id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, added LONG NOT NULL);")
            s.executeUpdate("CREATE TABLE tagged(item_id INT NOT NULL, tag_id INT NOT NULL, FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE, FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE, PRIMARY KEY (item_id, tag_id));")
            s.executeUpdate("CREATE TABLE groups(id INT NOT NULL PRIMARY KEY, title NVARCHAR(1024), FOREIGN KEY (id) REFERENCES items(id) ON DELETE CASCADE);")
            s.executeUpdate("CREATE TABLE media(id INT NOT NULL PRIMARY KEY, gid INT, page INT NOT NULL DEFAULT 0, path NVARCHAR(1024) UNIQUE, md5 NVARCHAR(32), hist_a BLOB, hist_r BLOB, hist_g BLOB, hist_b BLOB, no_similar BOOL NOT NULL DEFAULT FALSE, FOREIGN KEY (id) REFERENCES items(id) ON DELETE CASCADE, FOREIGN KEY (gid) REFERENCES groups(id) ON DELETE SET NULL);")
            s.executeUpdate("CREATE TABLE non_dupes(item_1 INT, item_2 INT, FOREIGN KEY (item_1) REFERENCES items(id) ON DELETE CASCADE, FOREIGN KEY (item_2) REFERENCES items(id) ON DELETE CASCADE);")
            s.executeUpdate("CREATE TABLE version(version INT NOT NULL PRIMARY KEY);")

            log.info("Updating version table to v8")
            s.executeUpdate("INSERT INTO version(version) VALUES (8);")
        }

        log.info("Finished migrating from v$fromVersion to v$toVersion in %.2fs".format((System.currentTimeMillis() - t) / 1000.0))
    }

}