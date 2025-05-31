package com.github.iguanastin.app.menagerie.database

import com.github.iguanastin.app.menagerie.model.ImageItem
import mu.KotlinLogging
import java.sql.ResultSet

private val log = KotlinLogging.logger {}

class DatabaseMigrator(val db: MenagerieDatabase) {

    private data class Migration(val from: Int, val to: Int, val run: () -> Unit)

    private val migrations = buildMap {
        listOf(
            Migration(-1, 8) { migrateInitial() },
            Migration(8, 9) { migrate8to9() },
            Migration(9, 10) { migrate9to10() },
            Migration(10, 11) { migrate10to11() },
        ).forEach { m -> put(m.from, m) }
    }


    fun needsMigration(): Boolean {
        return db.version != MenagerieDatabase.REQUIRED_DATABASE_VERSION
    }

    fun canMigrate(): Boolean {
        return needsMigration() && db.version in migrations && migrations[db.version]!!.to <= MenagerieDatabase.REQUIRED_DATABASE_VERSION
    }

    fun migrate() {
        log.info("Attempting to migrate database from v${db.version} to v${MenagerieDatabase.REQUIRED_DATABASE_VERSION}")
        db.updaterLock.acquireUninterruptibly()
//        db.db.autoCommit = false

        try{
            while (canMigrate()) {
                val m = migrations[db.version]
                    ?: throw MenagerieDatabaseException("No valid migration starting from ${db.version}")
                log.info("Migrating database from v${m.from} to v${m.to}")

                // TODO savepoints don't work. Upgrading H2 MIGHT fix it, idk.
//                val save = db.db.setSavepoint()
                try {
                    m.run()
//                    db.db.commit()
                } catch (e: Exception) {
                    log.error(e) { "Exception while migrating from v${m.from} to v${m.to}" }
//                    db.db.rollback()
                    throw e
                } finally {
//                    db.db.releaseSavepoint(save)
                }

                db.genericStatement.executeUpdate("INSERT INTO version(version) VALUES (${m.to});")
                db.version = m.to
                log.info("Finished migrating from v${m.from} to v${m.to}")
            }
        } finally {
//            db.db.autoCommit = true
            db.updaterLock.release()
        }

        log.info("Successfully migrated database to v${db.version}")
    }

    private fun migrateInitial() {
        val s = db.genericStatement

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
    }

    private fun migrate8to9() {
        val s = db.genericStatement

        // New grouping data
        log.info("Adding items column to groups table")
        s.executeUpdate("ALTER TABLE groups ADD COLUMN items NCLOB DEFAULT NULL;")
        db.db.prepareStatement("UPDATE groups SET items=? WHERE id=?;").use { ps ->
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
        db.db.prepareStatement("INSERT INTO files(id, md5, file) VALUES (?, ?, ?);").use { ps ->
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
        val dropMediaConditions = ImageItem.fileExtensions.joinToString(
            prefix = "LOWER(media.path) NOT LIKE '%.",
            separator = "' AND LOWER(media.path) NOT LIKE '%.",
            postfix = "'"
        )
        s.executeUpdate("DELETE FROM media WHERE $dropMediaConditions;")
        s.executeUpdate("ALTER TABLE media DROP COLUMN md5;")
        s.executeUpdate("ALTER TABLE media DROP COLUMN path;")
        s.executeUpdate("ALTER TABLE media RENAME TO images;")
    }

    private fun migrate9to10() {
        val s = db.genericStatement

        s.executeUpdate("CREATE TABLE similar(id1 INT NOT NULL, id2 INT NOT NULL, similarity DOUBLE PRECISION NOT NULL, FOREIGN KEY (id1) REFERENCES items(id) ON DELETE CASCADE, FOREIGN KEY (id2) REFERENCES items(id) ON DELETE CASCADE);")

        // Add migration time to version table
        s.executeUpdate("ALTER TABLE version ADD COLUMN time TIMESTAMP WITH TIME ZONE;")
        s.executeUpdate("ALTER TABLE version ALTER COLUMN time SET DEFAULT CURRENT_TIMESTAMP;")
    }

    private fun migrate10to11() {
        val s = db.genericStatement

        s.executeUpdate("CREATE TABLE imports(id INT NOT NULL, url NVARCHAR(1024), file NVARCHAR(1024), group_id INT, group_title NVARCHAR(1024), tags NCLOB);")
    }

}
