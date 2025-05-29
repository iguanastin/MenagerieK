package com.github.iguanastin.app.menagerie.database

import com.github.iguanastin.app.menagerie.database.migration.*
import com.github.iguanastin.app.menagerie.database.updates.*
import com.github.iguanastin.app.menagerie.import.Import
import com.github.iguanastin.app.menagerie.import.ImportGroup
import com.github.iguanastin.app.menagerie.model.*
import javafx.collections.ListChangeListener
import javafx.collections.SetChangeListener
import mu.KotlinLogging
import java.io.File
import java.sql.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

private val log = KotlinLogging.logger {}

class MenagerieDatabase(private val url: String, private val user: String, private val password: String) :
    AutoCloseable {

    companion object {
        const val MINIMUM_DATABASE_VERSION = 8
        const val REQUIRED_DATABASE_VERSION = 11

        val migrations: List<DatabaseMigration> = listOf(
            InitializeDatabaseV8(),
            MigrateDatabase8To9(),
            MigrateDatabase9To10(),
            MigrateDatabase10To11(),
        )
    }

    var db: Connection = DriverManager.getConnection("jdbc:h2:$url", user, password)
    private val preparedCache: MutableMap<String, PreparedStatement> = mutableMapOf()
    var genericStatement: Statement = db.createStatement()
        get() {
            if (field.isClosed) field = db.createStatement()
            return field
        }
        private set
    var version: Int = retrieveVersion()

    private val updateQueue: BlockingQueue<DatabaseUpdate> = LinkedBlockingQueue()
    private val updaterLock: Semaphore = Semaphore(1, true)

    @Volatile
    private var updateThreadRunning: Boolean = false

    val updateErrorHandlers: MutableList<(e: Exception) -> Unit> = mutableListOf()


    init {
        log.info("Starting database updater thread ($url)")
        thread(start = true, name = "Menagerie Database Updater") {
            updateThreadRunning = true
            while (updateThreadRunning) {
                updaterLock.acquireUninterruptibly()
                val update = updateQueue.poll(1, TimeUnit.SECONDS)

                if (!updateThreadRunning) {
                    updaterLock.release()
                    break
                }

                if (update != null) {
                    log.debug { "Update: $update" }

                    try {
                        update.sync(this)
                    } catch (e: Exception) {
                        updateErrorHandlers.forEach { it(e) }
                    }
                }

                updaterLock.release()
            }

            log.info("Database updater thread stopped ($url)")
        }
    }

    fun loadMenagerie(statusCallback: ((message: String) -> Unit)? = null): Menagerie {
        log.info("Loading Menagerie from database ($url)")

        val menagerie = Menagerie()

        val status = StatusFilter(statusCallback)
        loadTags(menagerie, status)
        loadItems(menagerie, status)
        loadTagged(menagerie, status)
        loadNonDupes(menagerie, status)
        loadSimilar(menagerie, status)
        loadImports(menagerie, status)

        attachTo(menagerie)

        log.info("Finished loading Menagerie from database ($url)")

        return menagerie
    }

    private fun attachTo(menagerie: Menagerie) {
        log.info("Attaching database manager to Menagerie ($url)")

        val changeListener: (ItemChangeBase) -> Unit = { change ->
            when (change) {
                is ImageItemChange -> {
                    if (change.histogram != null) updateQueue.put(
                        DatabaseSetImageHistogram(
                            change.item as ImageItem,
                            change.histogram.new
                        )
                    )
                    if (change.noSimilar != null) updateQueue.put(
                        DatabaseSetImageNoSimilar(
                            change.item as ImageItem,
                            change.noSimilar.new
                        )
                    )
                }
                is FileItemChange -> {
                    if (change.md5 != null) updateQueue.put(DatabaseSetFileMD5(change.item as FileItem, change.md5.new))
                    if (change.file != null) updateQueue.put(
                        DatabaseSetFilePath(
                            change.item as FileItem,
                            change.file.new
                        )
                    )
                }
                is GroupItemChange -> {
                    if (change.title != null) updateQueue.put(DatabaseSetGroupTitle(change.item.id, change.title.new))
                    if (!change.items.isNullOrEmpty()) updateQueue.put(
                        DatabaseSetGroupItems(
                            change.item as GroupItem,
                            change.items
                        )
                    )
                }
                is ItemChange -> {
                    if (change.tagAdded != null && !change.tagAdded.temporary) {
                        updateQueue.put(
                            DatabaseTagItem(
                                change.item,
                                change.tagAdded
                            )
                        )
                    }
                    if (change.tagRemoved != null && !change.tagRemoved.temporary) {
                        updateQueue.put(DatabaseUntagItem(change.item, change.tagRemoved))
                    }
                }
            }
        }

        for (item in menagerie.items) {
            item.changeListeners.add(changeListener)
        }

        menagerie.items.addListener(ListChangeListener { change ->
            while (change.next()) {
                change.removed.forEach { item ->
                    item.changeListeners.remove(changeListener)
                    updateQueue.put(DatabaseDeleteItem(item))
                }
                change.addedSubList.forEach { item ->
                    item.changeListeners.add(changeListener)
                    updateQueue.put(
                        when (item) {
                            is ImageItem -> DatabaseCreateImage(item)
                            is FileItem -> DatabaseCreateFile(item)
                            is GroupItem -> DatabaseCreateGroup(item)
                            else -> TODO("Unimplemented")
                        }
                    )
                }
            }
        })


        val tagChangeListener = { change: TagChange ->
            if (!change.tag.temporary && change.color != null) updateQueue.put(
                DatabaseSetTagColor(
                    change.tag,
                    change.color.new
                )
            )
        }
        for (tag in menagerie.tags) {
            tag.changeListeners.add(tagChangeListener)
        }
        menagerie.tags.addListener(SetChangeListener { change ->
            if (change.wasRemoved() && !change.elementRemoved.temporary) {
                updateQueue.put(DatabaseDeleteTag(change.elementRemoved))
                change.elementRemoved.changeListeners.remove(tagChangeListener)
            }
            if (change.wasAdded() && !change.elementAdded.temporary) {
                updateQueue.put(DatabaseCreateTag(change.elementAdded))
                change.elementAdded.changeListeners.add(tagChangeListener)
            }
        })


        menagerie.knownNonDupes.addListener(SetChangeListener { change ->
            if (change.wasRemoved()) updateQueue.put(DatabaseDeleteNonDupe(change.elementRemoved))
            if (change.wasAdded()) updateQueue.put(DatabaseCreateNonDupe(change.elementAdded))
        })


        menagerie.similarPairs.addListener(ListChangeListener { change ->
            while (change.next()) {
                change.removed.forEach { p -> updateQueue.put(DatabaseDeleteSimilar(p)) }
                change.addedSubList.forEach { p -> updateQueue.put(DatabaseCreateSimilar(p)) }
            }
        })

        menagerie.imports.addListener(ListChangeListener { change ->
            while (change.next()) {
                change.removed.forEach { p -> updateQueue.put(DatabaseDeleteImport(p)) }
                change.addedSubList.forEach { p -> updateQueue.put(DatabaseCreateImport(p)) }
            }
        })
    }

    fun needsMigration(): Boolean {
        return version < REQUIRED_DATABASE_VERSION || version > REQUIRED_DATABASE_VERSION
    }

    fun canMigrate(): Boolean {
        if (version < MINIMUM_DATABASE_VERSION || version > REQUIRED_DATABASE_VERSION) return false

        var v = version
        while (v < REQUIRED_DATABASE_VERSION) {
            val migration = getBestMigrationFor(v) ?: return false
            v = migration.toVersion
        }

        return true
    }

    fun migrateDatabase() {
        log.info("Attempting to migrate database from v$version to v$REQUIRED_DATABASE_VERSION")
        if (version in 0 until MINIMUM_DATABASE_VERSION) throw MenagerieDatabaseException("Minimum database version (v$MINIMUM_DATABASE_VERSION) not met (found v$version)")

        while (needsMigration()) {
            val migration = getBestMigrationFor(version)
                ?: throw MenagerieDatabaseException("No database migration path from v$version to v$REQUIRED_DATABASE_VERSION")

            try {
                log.info("Migrating database from v${migration.fromVersion} to v${migration.toVersion}")

                migration.migrate(db) // TODO if any error occurs, revert to backup database and throw error

                genericStatement.executeUpdate("INSERT INTO version(version) VALUES (${migration.toVersion});")
                log.info("Finished migrating from v${migration.fromVersion} to v${migration.toVersion}")
            } catch (e: Exception) {
                throw MenagerieDatabaseException(
                    "Exception during migration from v$version to v${migration.toVersion}",
                    e
                )
            }

            version = migration.toVersion
            log.info("Successfully migrated database to v$version")
        }
    }

    private fun getBestMigrationFor(version: Int): DatabaseMigration? {
        var migration: DatabaseMigration? = null

        for (i in migrations.indices) {
            if (migrations[i].fromVersion != version) continue
            if (migration == null || migrations[i].toVersion > migration.toVersion) {
                migration = migrations[i]
            }
        }

        return migration
    }

    private fun loadTags(menagerie: Menagerie, status: StatusFilter) {
        log.info("Loading tags from database")
        status.force("Fetching tag data...", mark = true)
        var i = 0
        genericStatement.executeQuery("SELECT * FROM tags;").use { rs: ResultSet ->
            while (rs.next()) {
                menagerie.addTag(Tag(rs.getInt("id"), rs.getNString("name"), menagerie, color = rs.getNString("color")))
                i++

                status.trySend { "Loaded $i tags..." }
            }
        }
        log.info("Successfully loaded $i tags from database in ${status.sinceMarkStr()}")
    }

    private fun loadItems(menagerie: Menagerie, status: StatusFilter) {
        log.info("Loading items from database")
        status.force("Fetching item data (this may take a while)...", mark = true)
        var count = 0
        val images: MutableMap<Int, TempImageV9> = mutableMapOf()
        genericStatement.executeQuery("SELECT * FROM images;").use { rs ->
            while (rs.next()) {
                var histogram: Histogram? = null

                val alpha = rs.getBinaryStream("hist_a")
                if (alpha != null) {
                    histogram = Histogram.from(
                        alpha,
                        rs.getBinaryStream("hist_r"),
                        rs.getBinaryStream("hist_g"),
                        rs.getBinaryStream("hist_b")
                    )
                }

                images[rs.getInt("id")] = TempImageV9(rs.getBoolean("no_similar"), histogram)
                count++

                status.trySend { "Loaded $count images..." }
            }
        }
        log.info("Finished loading $count image items in ${status.sinceMarkStr()}")

        status.markNow()
        count = 0
        val files: MutableMap<Int, TempFileV9> = mutableMapOf()
        genericStatement.executeQuery("SELECT * FROM files;").use { rs ->
            while (rs.next()) {
                files[rs.getInt("id")] = TempFileV9(rs.getNString("md5"), File(rs.getNString("file")))
                count++

                status.trySend { "Loaded $count files..." }
            }
        }
        log.info("Finished loading $count file items in ${status.sinceMarkStr()}")

        status.markNow()
        count = 0
        val items: MutableMap<Int, TempItemV9> = mutableMapOf()
        genericStatement.executeQuery("SELECT * FROM items;").use { rs ->
            while (rs.next()) {
                val id = rs.getInt("id")
                items[id] = TempItemV9(id, rs.getLong("added"))
                count++

                status.trySend { "Loaded $count items..." }
            }
        }
        log.info("Finished loading $count items in ${status.sinceMarkStr()}")

        status.markNow()
        count = 0
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
                count++

                status.trySend { "Loaded $count groups..." }
            }
        }
        log.info("Finished loading $count group items in ${status.sinceMarkStr()}")


        status.force("Mapping item data...", mark = true)
        val generatedItems: MutableList<Item> = mutableListOf()
        val groupChildMap: MutableMap<Int, FileItem> = mutableMapOf()
        for (i in images.keys) {
            val item = ImageItem(
                i,
                items[i]!!.added,
                menagerie,
                files[i]!!.md5,
                files[i]!!.file,
                images[i]!!.noSimilar,
                images[i]!!.histogram
            )
            generatedItems.add(item)
            groupChildMap[i] = item
            files.remove(i)
            items.remove(i)
        }
        for (i in files.keys) {
            val item = if (VideoItem.isVideo(files[i]!!.file)) {
                VideoItem(i, items[i]!!.added, menagerie, files[i]!!.md5, files[i]!!.file)
            } else {
                FileItem(i, items[i]!!.added, menagerie, files[i]!!.md5, files[i]!!.file)
            }
            generatedItems.add(item)
            groupChildMap[i] = item
            items.remove(i)
        }
        for (i in groups.keys) {
            val group = GroupItem(i, items[i]!!.added, menagerie, groups[i]!!.title)
            groups[i]!!.items.forEach { id ->
                if (groupChildMap.containsKey(id)) group.addItem(groupChildMap[id]!!)
            }
            generatedItems.add(group)
            items.remove(i)
        }
        for (i in items.keys) {
            log.error { "Orphaned item: (id:$i, added:${items[i]!!.added})" }
        }

        generatedItems.sortBy { it.id } // TODO this smells
        generatedItems.forEach { menagerie.addItem(it) }

        log.info("Finished generating ${generatedItems.size} loaded items in ${status.sinceMarkStr()}")
    }

    private fun loadTagged(menagerie: Menagerie, status: StatusFilter) {
        log.info("Loading tag relationships from database")
        status.force("Fetching tag-relationship data...", mark = true)
        var i = 0
        genericStatement.executeQuery("SELECT * FROM tagged;").use { rs: ResultSet ->
            while (rs.next()) {
                val tag = menagerie.getTag(rs.getInt("tag_id"))
                val item = menagerie.getItem(rs.getInt("item_id"))
                if (tag == null || item == null) continue
                item.addTag(tag)
                i++

                status.trySend { "Loaded $i tag relationships..." }
            }
        }
        log.info("Finished loading $i tag relationships in ${status.sinceMarkStr()}")
    }

    private fun loadNonDupes(menagerie: Menagerie, status: StatusFilter) {
        log.info("Loading non-dupes from database")
        status.force("Fetching non-dupe data...", mark = true)
        var i = 0
        genericStatement.executeQuery("SELECT item_1, item_2 FROM non_dupes;").use { rs: ResultSet ->
            while (rs.next()) {
                val i1 = menagerie.getItem(rs.getInt("item_1"))
                val i2 = menagerie.getItem(rs.getInt("item_2"))
                if (i1 == null || i2 == null) continue
                menagerie.addNonDupe(SimilarPair(i1, i2, 1.0))
                i++

                status.trySend { "Loaded $i non-dupes..." }
            }
        }
        log.info("Finished loading $i non-dupes in ${status.sinceMarkStr()}")
    }

    private fun loadSimilar(menagerie: Menagerie, status: StatusFilter) {
        log.info("Loading similar pairs")
        status.force("Fetching similar pairs...", mark = true)
        var i = 0
        genericStatement.executeQuery("SELECT id1,id2,similarity FROM similar;").use { rs: ResultSet ->
            while (rs.next()) {
                val i1 = menagerie.getItem(rs.getInt("id1"))
                val i2 = menagerie.getItem(rs.getInt("id2"))
                if (i1 == null || i2 == null) continue
                val similarity = rs.getDouble("similarity")
                menagerie.addSimilarity(SimilarPair(i1, i2, similarity))
                i++

                status.trySend { "Loaded $i similar pairs..." }
            }
        }
        log.info("Finished loading $i similar pairs in ${status.sinceMarkStr()}")
    }

    private fun loadImports(menagerie: Menagerie, status: StatusFilter) {
        log.info("Loading imports")
        status.force("Fetching imports...", mark = true)
        var i = 0
        genericStatement.executeQuery("SELECT id,url,file,group_title,group_id,tags FROM imports;").use { rs ->
            while (rs.next()) {
                val id = rs.getInt("id")
                val url = rs.getNString("url")
                val file = File(rs.getNString("file"))
                var groupId: Int? = rs.getInt("group_id")
                if (rs.wasNull()) groupId = null
                val group = if (groupId != null) ImportGroup(rs.getNString("group_title"), groupId) else null
                val tags = rs.getNClob("tags").characterStream.use { t -> t.readText() }.split(',')
                    .map { t -> menagerie.getOrMakeTag(t, temporaryIfNew = true) }
                val job = Import(id, url, file, group, tags)
                menagerie.imports.add(job)

                i++
                status.trySend { "Loaded $i imports..." }
            }
        }
        log.info("Finished loading $i imports in ${status.sinceMarkStr()}")
    }

    private fun retrieveVersion(): Int {
        try {
            genericStatement.executeQuery("SELECT TOP 1 version.version FROM version ORDER BY version.version DESC;")
                .use { rs ->
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

    fun getPrepared(key: String, statement: String): PreparedStatement {
        var ps = preparedCache[key]
        if (ps == null) {
            ps = db.prepareStatement(statement)!!
            preparedCache[key] = ps
        }
        return ps
    }

    @Suppress("UNREACHABLE_CODE")
    fun backupDatabase() {
        log.info("Shutting down database for backup...")
        updaterLock.acquireUninterruptibly()

        closeAndCompress(false)

        TODO("Backup of database file unimplemented")

        log.info("Restarting database connection...")
        db = DriverManager.getConnection("jdbc:h2:$url", user, password)
        genericStatement = db.createStatement()

        updaterLock.release()
        log.info("Finished backing up database")
    }

    private fun closeAndCompress(stopUpdater: Boolean) {
        log.info("Shutting down database with defrag...")
        val t = System.currentTimeMillis()
        genericStatement.execute("SHUTDOWN DEFRAG;")
        log.info("Finished defragging database in %.2fs".format((System.currentTimeMillis() - t) / 1000.0))
        close(stopUpdater)
    }

    /**
     * NOTE: Long blocking call
     */
    fun closeAndCompress() {
        closeAndCompress(true)
    }

    private fun close(stopUpdater: Boolean) {
        preparedCache.values.forEach { ps -> ps.close() }
        preparedCache.clear()
        genericStatement.close()
        db.close()
        updateThreadRunning = !stopUpdater
        log.info("Closed database manager ($url)")
    }

    override fun close() {
        close(true)
    }

}