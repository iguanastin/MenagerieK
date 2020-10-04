package com.github.iguanastin.app.menagerie.database

import com.github.iguanastin.app.menagerie.ImageItem
import java.sql.Connection
import java.sql.ResultSet


abstract class DatabaseMigration {

    abstract val fromVersion: Int
    abstract val toVersion: Int

    abstract fun migrate(db: Connection)

}


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


class MigrateDatabase8To9 : DatabaseMigration() {

    override val fromVersion: Int = 8
    override val toVersion: Int = 9


    override fun migrate(db: Connection) {
        db.createStatement().use { s ->
            s.executeUpdate("CREATE TABLE grouped(group_id INT NOT NULL, item_id INT NOT NULL, index INT NOT NULL, FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE, FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE, PRIMARY KEY (group_id, item_id));")
            db.prepareStatement("INSERT INTO grouped(group_id, item_id, index) VALUES (?, ?, ?);").use { ps ->
                s.executeQuery("SELECT media.id, media.gid, media.page FROM media WHERE media.gid IS NOT NULL;").use { rs: ResultSet ->
                    while (rs.next()) {
                        val gid = rs.getInt("media.gid")
                        val iid = rs.getInt("media.id")
                        val index = rs.getInt("media.page")
                        ps.setInt(1, gid)
                        ps.setInt(2, iid)
                        ps.setInt(3, index)
                        ps.addBatch()
                    }
                }
                ps.executeBatch()
            }
            s.executeUpdate("ALTER TABLE media DROP COLUMN gid;")
            s.executeUpdate("ALTER TABLE media DROP COLUMN page;")

            s.executeUpdate("CREATE TABLE files(id INT NOT NULL, md5 NVARCHAR(32), file NVARCHAR(1024) UNIQUE, FOREIGN KEY (id) REFERENCES items(id) ON DELETE CASCADE);")
            db.prepareStatement("INSERT INTO files(id, md5, file) VALUES (?, ?, ?);").use { ps ->
                s.executeQuery("SELECT media.id, media.path, media.md5 FROM media;").use { rs: ResultSet ->
                    while (rs.next()) {
                        val id = rs.getInt("media.id")
                        val md5 = rs.getNString("media.md5")
                        val file = rs.getNString("media.path")
                        ps.setInt(1, id)
                        ps.setNString(2, md5)
                        ps.setNString(3, file)
                        ps.addBatch()
                    }
                }
                ps.executeBatch()
            }
            s.executeBatch()
            val dropMediaConditions = ImageItem.fileExtensions.joinToString(prefix = "LOWER(media.path) NOT LIKE '%.", separator = " AND LOWER(media.path) NOT LIKE '%.", postfix = "'")
            s.executeUpdate("DELETE FROM media WHERE $dropMediaConditions;")
            s.executeUpdate("ALTER TABLE media DROP COLUMN md5;")
            s.executeUpdate("ALTER TABLE media DROP COLUMN path;")
            s.executeUpdate("ALTER TABLE media RENAME TO images;")
        }
    }

}