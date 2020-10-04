package com.github.iguanastin.app.menagerie.database.migration

import com.github.iguanastin.app.menagerie.ImageItem
import java.sql.Connection
import java.sql.ResultSet


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