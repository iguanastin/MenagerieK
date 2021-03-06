package com.github.iguanastin.app.menagerie.database.migration

import com.github.iguanastin.app.menagerie.model.ImageItem
import mu.KotlinLogging
import java.sql.Connection
import java.sql.ResultSet


private val log = KotlinLogging.logger {}
class MigrateDatabase8To9 : DatabaseMigration() {

    override val fromVersion: Int = 8
    override val toVersion: Int = 9


    override fun migrate(db: Connection) {
        log.info("Migrating database from v$fromVersion to v$toVersion")
        val t = System.currentTimeMillis()

        db.createStatement().use { s ->
            // New grouping data
            log.info("Adding items column to groups table")
            s.executeUpdate("ALTER TABLE groups ADD COLUMN items NCLOB DEFAULT NULL;")
            db.prepareStatement("UPDATE groups SET items=? WHERE id=?;").use { ps ->
                val gids: MutableList<Int> = mutableListOf()

                s.executeQuery("SELECT id FROM groups;").use { rs ->
                    while (rs.next()) {
                        gids.add(rs.getInt("id"))
                    }
                }

                for (gid in gids) {
                    val items: MutableMap<Int, Int> = mutableMapOf()

                    s.executeQuery("SELECT id, page FROM media WHERE gid=$gid;").use { rs ->
                        while (rs.next()) {
                            items[rs.getInt("id")] = rs.getInt("page")
                        }
                    }

                    ps.setNClob(1, items.keys.sortedBy { items[it] }.joinToString(separator = ",").reader())
                    ps.setInt(2, gid)
                    ps.addBatch()
                }

                ps.executeBatch()
            }


            // New files table
            log.info("Creating files table and moving media data into it")
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
            log.info("Cleaning up media table and renaming to images")
            val dropMediaConditions = ImageItem.fileExtensions.joinToString(prefix = "LOWER(media.path) NOT LIKE '%.", separator = "' AND LOWER(media.path) NOT LIKE '%.", postfix = "'")
            s.executeUpdate("DELETE FROM media WHERE $dropMediaConditions;")
            s.executeUpdate("ALTER TABLE media DROP COLUMN md5;")
            s.executeUpdate("ALTER TABLE media DROP COLUMN path;")
            s.executeUpdate("ALTER TABLE media RENAME TO images;")

            s.executeUpdate("INSERT INTO version(version) VALUES (9);")
        }

        log.info("Finished migrating from v$fromVersion to v$toVersion in %.2fs".format((System.currentTimeMillis() - t) / 1000.0))
    }

}