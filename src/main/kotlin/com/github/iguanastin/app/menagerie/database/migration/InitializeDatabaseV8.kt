package com.github.iguanastin.app.menagerie.database.migration

import java.sql.Connection


class InitializeDatabaseV8 : DatabaseMigration() {

    override val fromVersion: Int = -1
    override val toVersion: Int = 8

    companion object {
        private const val sqlDropTables = "DROP TABLE IF EXISTS imgs; DROP TABLE IF EXISTS tags; DROP TABLE IF EXISTS tagged; DROP TABLE IF EXISTS version; DROP TABLE IF EXISTS items; DROP TABLE IF EXISTS groups; DROP TABLE IF EXISTS media; DROP TABLE IF EXISTS non_dupes; DROP TABLE IF EXISTS tag_notes;"
        private const val sqlCreateVersionTable = "CREATE TABLE version(version INT NOT NULL PRIMARY KEY);"
        private const val sqlUpdateVersion = "INSERT INTO version(version) VALUES (8);"
        private const val sqlCreateTagsTable = "CREATE TABLE tags(id INT PRIMARY KEY AUTO_INCREMENT, name NVARCHAR(128) NOT NULL UNIQUE, color NVARCHAR(32));"
        private const val sqlCreateItemsTable = "CREATE TABLE items(id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, added LONG NOT NULL);"
        private const val sqlCreateTaggedTable = "CREATE TABLE tagged(item_id INT NOT NULL, tag_id INT NOT NULL, FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE, FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE, PRIMARY KEY (item_id, tag_id));"
        private const val sqlCreateGroupsTable = "CREATE TABLE groups(id INT NOT NULL PRIMARY KEY, title NVARCHAR(1024), FOREIGN KEY (id) REFERENCES items(id) ON DELETE CASCADE);"
        private const val sqlCreateMediaTable = "CREATE TABLE media(id INT NOT NULL PRIMARY KEY, gid INT, page INT NOT NULL DEFAULT 0, path NVARCHAR(1024) UNIQUE, md5 NVARCHAR(32), hist_a BLOB, hist_r BLOB, hist_g BLOB, hist_b BLOB, no_similar BOOL NOT NULL DEFAULT FALSE, FOREIGN KEY (id) REFERENCES items(id) ON DELETE CASCADE, FOREIGN KEY (gid) REFERENCES groups(id) ON DELETE SET NULL);"
        private const val sqlCreateTagNotesTable = "CREATE TABLE tag_notes(tag_id INT, note NVARCHAR(1024), FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE);"
        private const val sqlCreateNonDupesTable = "CREATE TABLE non_dupes(item_1 INT, item_2 INT, FOREIGN KEY (item_1) REFERENCES items(id) ON DELETE CASCADE, FOREIGN KEY (item_2) REFERENCES items(id) ON DELETE CASCADE);"
    }


    override fun migrate(db: Connection) {
        db.createStatement().use { s ->
            s.executeUpdate(sqlDropTables)

            s.executeUpdate(sqlCreateTagsTable)
            s.executeUpdate(sqlCreateTagNotesTable)
            s.executeUpdate(sqlCreateItemsTable)
            s.executeUpdate(sqlCreateTaggedTable)
            s.executeUpdate(sqlCreateGroupsTable)
            s.executeUpdate(sqlCreateMediaTable)
            s.executeUpdate(sqlCreateNonDupesTable)

            s.executeUpdate(sqlCreateVersionTable)
            s.executeUpdate(sqlUpdateVersion)
        }
    }

}