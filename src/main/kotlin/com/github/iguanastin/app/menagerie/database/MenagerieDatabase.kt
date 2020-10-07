package com.github.iguanastin.app.menagerie.database

import com.github.iguanastin.app.menagerie.*
import com.github.iguanastin.app.menagerie.database.migration.DatabaseMigration
import com.github.iguanastin.app.menagerie.database.migration.InitializeDatabaseV8
import com.github.iguanastin.app.menagerie.database.migration.MigrateDatabase8To9
import com.github.iguanastin.app.menagerie.database.updates.*
import javafx.collections.ListChangeListener
import java.io.File
import java.sql.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class MenagerieDatabase(url: String, user: String, password: String) : AutoCloseable {

    companion object {
        const val MINIMUM_DATABASE_VERSION = 8
        const val REQUIRED_DATABASE_VERSION = 9

        val migrations: List<DatabaseMigration> = listOf(InitializeDatabaseV8(), MigrateDatabase8To9())
    }

    private val db: Connection = DriverManager.getConnection("jdbc:h2:$url", user, password)
    var genericStatement: Statement = db.createStatement()
        get() {
            if (field.isClosed) field = db.createStatement()
            return field
        }
        private set
    var version: Int = retrieveVersion()
    private val preparedCache: Map<String, PreparedStatement> = mutableMapOf()

    private val updateQueue: BlockingQueue<DatabaseUpdate> = LinkedBlockingQueue()

    @Volatile
    private var updateThreadRunning: Boolean = false

    val updateErrorHandlers: MutableList<(e: Exception) -> Unit> = mutableListOf()


    init {
        thread(start = true, name = "Menagerie Database Updater") {
            updateThreadRunning = true
            while (updateThreadRunning) {
                val update = updateQueue.poll(3, TimeUnit.SECONDS)
                if (!updateThreadRunning) break
                if (update == null) continue

                println("Applying update: $update")

                try {
                    update.sync(this)
                } catch (e: Exception) {
                    updateErrorHandlers.forEach { it(e) }
                }
            }

            println("Database Updater thread finished")
        }
    }


    fun getCachedStatement(owner: DatabaseUpdate, key: String): PreparedStatement {
        return preparedCache["${owner.javaClass.name}.$key"] ?: owner.prepareStatement(db, key)
    }

    fun loadMenagerie(): Menagerie {
        if (needsMigration()) migrateDatabase()

        val menagerie = Menagerie()

        loadTags(menagerie)
        loadItems(menagerie)
        loadTagged(menagerie)
        loadNonDupes(menagerie)

        attachTo(menagerie)

        return menagerie
    }

    private fun attachTo(menagerie: Menagerie) {
        val itemTaggedListener = { item: Item, tag: Tag ->
            updateQueue.put(DatabaseTagItem(item, tag))
        }
        val itemUntaggedListener = { item: Item, tag: Tag ->
            updateQueue.put(DatabaseUntagItem(item, tag))
        }

        menagerie.items.addListener(ListChangeListener { change ->
            while (change.next()) {
                change.removed.forEach { item ->
                    item.untagListeners.add(itemUntaggedListener)
                    item.tagListeners.add(itemTaggedListener)
                    updateQueue.put(DatabaseDeleteItem(item))
                }
                change.addedSubList.forEach { item ->
                    item.untagListeners.remove(itemUntaggedListener)
                    item.tagListeners.remove(itemTaggedListener)
                    when (item) {
                        is ImageItem -> updateQueue.put(DatabaseCreateMedia(item))
                        else -> TODO("Unimplemented")
                    }
                }
            }
        })

        menagerie.tags.addListener(ListChangeListener { change ->
            while (change.next()) {
                change.removed.forEach { tag -> updateQueue.put(DatabaseDeleteTag(tag)) }
                change.addedSubList.forEach { tag -> updateQueue.put(DatabaseCreateTag(tag)) }
            }
        })

        menagerie.knownNonDupes.addListener(ListChangeListener { change ->
            while (change.next()) {
                change.removed.forEach { pair -> updateQueue.put(DatabaseDeleteNonDupe(pair)) }
                change.addedSubList.forEach { pair -> updateQueue.put(DatabaseCreateNonDupe(pair)) }
            }
        })
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
                migration.migrate(db) // TODO if any error occurs, revert to backup database and throw error
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
        genericStatement.executeQuery("SELECT * FROM tags;").use { rs: ResultSet ->
            while (rs.next()) {
                menagerie.addTag(Tag(rs.getInt("id"), rs.getNString("name"), color = rs.getNString("color")))
            }
        }
    }

    private fun loadItems(menagerie: Menagerie) {
        val images: MutableMap<Int, TempImageV9> = mutableMapOf()
        genericStatement.executeQuery("SELECT * FROM images;").use { rs ->
            while (rs.next()) {
                var histogram: Histogram? = null

                val alpha = rs.getBinaryStream("hist_a")
                if (alpha != null) {
                    histogram = Histogram.from(alpha, rs.getBinaryStream("hist_r"), rs.getBinaryStream("hist_g"), rs.getBinaryStream("hist_b"))
                }

                images[rs.getInt("id")] = TempImageV9(rs.getBoolean("no_similar"), histogram)
            }
        }

        val files: MutableMap<Int, TempFileV9> = mutableMapOf()
        genericStatement.executeQuery("SELECT * FROM files;").use { rs ->
            while (rs.next()) {
                files[rs.getInt("id")] = TempFileV9(rs.getNString("md5"), File(rs.getNString("file")))
            }
        }

        val items: MutableMap<Int, TempItemV9> = mutableMapOf()
        genericStatement.executeQuery("SELECT * FROM items;").use { rs ->
            while (rs.next()) {
                val id = rs.getInt("id")
                items[id] = TempItemV9(id, rs.getLong("added"))
            }
        }

        val groups: MutableMap<Int, TempGroupV9> = mutableMapOf()
        genericStatement.executeQuery("SELECT * FROM groups;").use { rs ->
            while (rs.next()) {
                val ids: MutableList<Int> = mutableListOf()
                rs.getNClob("items").characterStream.use {
                    for (i in it.readText().split(",")) {
                        if (i.isEmpty()) continue
                        ids.add(i.toInt())
                    }
                }
                groups[rs.getInt("id")] = TempGroupV9(rs.getNString("title"), ids)
            }
        }


        val generatedItems: MutableList<Item> = mutableListOf()
        val groupChildMap: MutableMap<Int, FileItem> = mutableMapOf()
        for (i in images.keys) {
            val item = ImageItem(i, items[i]!!.added, files[i]!!.md5, files[i]!!.file, images[i]!!.noSimilar, images[i]!!.histogram)
            generatedItems.add(item)
            groupChildMap[i] = item
            files.remove(i)
            items.remove(i)
        }
        for (i in files.keys) {
            val item = FileItem(i, items[i]!!.added, files[i]!!.md5, files[i]!!.file)
            generatedItems.add(item)
            groupChildMap[i] = item
            items.remove(i)
        }
        for (i in groups.keys) {
            val group = GroupItem(i, items[i]!!.added, groups[i]!!.title)
            groups[i]!!.items.forEach { id ->
                group.addItem(groupChildMap[id]!!)
            }
            generatedItems.add(group)
            items.remove(i)
        }
        for (i in items.keys) {
            println("Orphaned item: (id:$i, added:${items[i]!!.added})")
        }

        generatedItems.sortBy { it.id }
        generatedItems.forEach { menagerie.addItem(it) }
    }

    private fun loadTagged(menagerie: Menagerie) {
        genericStatement.executeQuery("SELECT * FROM tagged;").use { rs: ResultSet ->
            while (rs.next()) {
                val tag = menagerie.getTag(rs.getInt("tag_id"))
                val item = menagerie.getItem(rs.getInt("item_id"))
                if (tag == null || item == null) continue
                item.addTag(tag)
            }
        }
    }

    private fun loadNonDupes(menagerie: Menagerie) {
        genericStatement.executeQuery("SELECT item_1, item_2 FROM non_dupes;").use { rs: ResultSet ->
            while (rs.next()) {
                val i1 = menagerie.getItem(rs.getInt("item_1"))
                val i2 = menagerie.getItem(rs.getInt("item_2"))
                if (i1 == null || i2 == null) continue
                menagerie.addNonDupe(i1 to i2)
            }
        }
    }

    private fun retrieveVersion(): Int {
        try {
            genericStatement.executeQuery("SELECT TOP 1 version.version FROM version ORDER BY version.version DESC;").use { rs ->
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
                genericStatement.executeQuery("SELECT TOP 1 * FROM imgs;").use {
                    // Tables exist for version 0
                    return 0
                }
            } catch (e2: SQLException) {
                // Tables don't exist or are not clean
                return -1
            }
        }
    }

    /**
     * NOTE: Long blocking call
     */
    fun closeAndCompress() {
        genericStatement.execute("SHUTDOWN DEFRAG;")
        close()
    }

    override fun close() {
        preparedCache.values.forEach { ps -> ps.close() }
        genericStatement.close()
        db.close()
        updateThreadRunning = false
    }

}