package com.github.iguanastin.app.menagerie.database

import com.github.iguanastin.app.menagerie.*
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException

class MenagerieDatabaseException : Exception {
    constructor() : super()
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
    constructor(cause: Throwable) : super(cause)
    constructor(message: String, cause: Throwable, enableSuppression: Boolean, writableStackTrace: Boolean) : super(message, cause, enableSuppression, writableStackTrace)
}

class MenagerieDatabase(url: String, user: String, password: String) {

    companion object {
        const val MINIMUM_DATABASE_VERSION = 8
        const val REQUIRED_DATABASE_VERSION = 8

        val migrations: List<DatabaseMigration> = listOf(InitializeDatabaseV8(), MigrateDatabase8To9())
    }

    private val db: Connection = DriverManager.getConnection("jdbc:h2:$url", user, password)
    var version: Int = retrieveVersion()

    private val psTags by lazy { db.prepareStatement("SELECT * FROM tags;") }
    private val psMedia by lazy { db.prepareStatement("SELECT * FROM media JOIN items ON items.id=media.id;") }
    private val psTagged by lazy { db.prepareStatement("SELECT * FROM tagged;") }
    private val psNonDupes by lazy { db.prepareStatement("SELECT item_1, item_2 FROM non_dupes;") }


    fun loadMenagerie(): Menagerie {
        if (needsMigration()) migrateDatabase()

        val menagerie = Menagerie(tags = null)

        loadTags(menagerie)
        loadMediaItems(menagerie)
        loadTagged(menagerie)
        loadNonDupes(menagerie)

        return menagerie
    }

    fun needsMigration(): Boolean {
        return version < REQUIRED_DATABASE_VERSION
    }

    fun canMigrate(): Boolean {
        if (version in 0 until MINIMUM_DATABASE_VERSION) return false

        var v = version
        while (v < REQUIRED_DATABASE_VERSION) {
            val migration = getBestMigrationFor(v) ?: return false
            v = migration.toVersion
        }

        return true
    }

    fun migrateDatabase() {
        if (version in 0 until MINIMUM_DATABASE_VERSION) throw MenagerieDatabaseException("Minimum database version (v$MINIMUM_DATABASE_VERSION) not met (found v$version)")

        while (needsMigration()) {
            val migration = getBestMigrationFor(version)
                    ?: throw MenagerieDatabaseException("No database migration path from v$version to v$REQUIRED_DATABASE_VERSION")

            try {
                migration.migrate(db)
            } catch (e: Exception) {
                throw MenagerieDatabaseException("Exception during migration from v$version to v${migration.toVersion}", e)
            }

            version = migration.toVersion
        }
    }

    private fun getBestMigrationFor(version: Int): DatabaseMigration? {
        var migration: DatabaseMigration? = null

        for (i in migrations.indices) {
            if (migrations[i].fromVersion == version) {
                if (migration == null || migrations[i].toVersion > migration.toVersion) {
                    migration = migrations[i]
                }
            }
        }

        return migration
    }

    private fun loadTags(menagerie: Menagerie) {
        psTags.executeQuery().use { rs: ResultSet ->
            while (rs.next()) {
                menagerie.tags.add(Tag(rs.getInt("id"), rs.getNString("name"), color = rs.getNString("color")))
            }
        }
    }

    private fun loadMediaItems(menagerie: Menagerie) {
        psMedia.executeQuery().use { rs: ResultSet ->
            while (rs.next()) {
                var hist: Histogram? = null
                val alpha = rs.getBinaryStream("media.hist_a")
                if (alpha != null) {
                    hist = Histogram(alpha, rs.getBinaryStream("media.hist_r"), rs.getBinaryStream("media.hist_g"), rs.getBinaryStream("media.hist_b"))
                }

                val id = rs.getInt("items.id")
                val added = rs.getLong("items.added")
                val md5 = rs.getNString("media.md5")
                val noSimilar = rs.getBoolean("media.no_similar")
                val file = File(rs.getNString("media.path"))

                // TODO other types (better solution?)
                val item = if (ImageItem.isImage(file)) {
                    ImageItem(id, added, md5, file, noSimilar = noSimilar, histogram = hist)
                } else {
                    FileItem(id, added, md5, file)
                }

                menagerie.items.add(item)
            }
        }
    }

    private fun loadTagged(menagerie: Menagerie) {
        psTagged.executeQuery().use { rs: ResultSet ->
            while (rs.next()) {
                val tag = menagerie.getTag(rs.getInt("tag_id"))
                val item = menagerie.getItem(rs.getInt("item_id"))
                if (tag == null || item == null) continue
                item.tags.add(tag)
            }
        }
    }

    private fun loadNonDupes(menagerie: Menagerie) {
        psNonDupes.executeQuery().use { rs: ResultSet ->
            while (rs.next()) {
                val i1 = menagerie.getItem(rs.getInt("item_1"))
                val i2 = menagerie.getItem(rs.getInt("item_2"))
                if (i1 == null || i2 == null) continue
                menagerie.knownNonDupes.add(i1 to i2)
            }
        }
    }

    private fun retrieveVersion(): Int {
        db.createStatement().use { s ->
            try {
                s.executeQuery("SELECT TOP 1 version.version FROM version ORDER BY version.version DESC;").use { rs ->
                    return if (rs.next()) {
                        rs.getInt("version")
                    } else {
                        // No version information in existing version table, probably corrupted
                        -1
                    }
                }
            } catch (e: SQLException) {
                //Database is either version 0 schema or not initialized
                try {
                    s.executeQuery("SELECT TOP 1 * FROM imgs;").use {
                        // Tables exist for version 0
                        return 0
                    }
                } catch (e2: SQLException) {
                    // Tables don't exist or are not clean
                    return -1
                }
            }
        }
    }

}