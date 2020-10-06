package com.github.iguanastin.app.menagerie.database.migration

import com.github.iguanastin.app.menagerie.ImageItem
import java.sql.Connection
import java.sql.ResultSet


class MigrateDatabase8To9 : DatabaseMigration() {

    override val fromVersion: Int = 8
    override val toVersion: Int = 9


    override fun migrate(db: Connection) {
        db.createStatement().use { s ->
            // New grouping data
            s.executeUpdate("ALTER TABLE groups ADD COLUMN items NCLOB DEFAULT NULL;")
            db.prepareStatement("UPDATE groups SET items=? WHERE id=?;").use { ps ->
                s.executeQuery("SELECT id FROM groups;").use { rs ->
                    while (rs.next()) {
                        val gid = rs.getInt("id")
                        val items: MutableMap<Int, Int> = mutableMapOf()

                        s.executeQuery("SELECT id, page FROM media WHERE gid=$gid;").use { rs2 ->
                            while (rs2.next()) {
                                items[rs2.getInt("id")] = rs2.getInt("page")
                            }
                        }

                        ps.setNClob(1, items.keys.sortedBy { items[it] }.joinToString(separator = ",").reader())
                        ps.setInt(2, gid)
                        ps.addBatch()
                    }
                }

                ps.executeBatch()
            }


            // New files table
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