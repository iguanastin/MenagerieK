package com.github.iguanastin.app.menagerie.model

import com.github.iguanastin.app.menagerie.duplicates.local.CPUDuplicateFinder
import com.github.iguanastin.app.menagerie.import.Import
import javafx.collections.*
import javafx.scene.image.Image
import tornadofx.*
import java.io.File
import java.lang.ref.SoftReference
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

class Menagerie {

    private val _tags: ObservableSet<Tag> = FXCollections.observableSet()
    val tags: ObservableSet<Tag> = _tags.asUnmodifiable()
    private val tagIdMap: MutableMap<Int, Tag> = mutableMapOf()
    private val nextTagID = AtomicInteger(0)

    private val _items: ObservableList<Item> = FXCollections.observableArrayList()
    val items: ObservableList<Item> = _items.asUnmodifiable()
    private val itemIdMap: MutableMap<Int, Item> = mutableMapOf()
    private val nextItemID = AtomicInteger(0)
    private val files: MutableSet<File> = mutableSetOf()

    private val _knownNonDupes: ObservableSet<SimilarPair<Item>> = FXCollections.observableSet()
    val knownNonDupes: ObservableSet<SimilarPair<Item>> = _knownNonDupes.asUnmodifiable()

    private val _similarPairs: ObservableList<SimilarPair<Item>> = FXCollections.observableArrayList()
    val similarPairs = _similarPairs.asUnmodifiable()

    private val _imports: ObservableList<Import> = FXCollections.observableArrayList()
    val imports = _imports.asUnmodifiable()
    private val nextImportID = AtomicInteger(0)
    private val nextImportTempGroupID = AtomicInteger(-1)

    var similarityConfidence: Double = 0.95


    init {
        items.addListener(ListChangeListener { change ->
            while (change.next()) {
                change.removed.forEach { itemRemoved(it) }
                change.addedSubList.forEach { itemAdded(it) }
            }
        })
        tags.addListener(SetChangeListener { change ->
            if (change.wasRemoved()) tagIdMap.remove(change.elementRemoved.id)
            if (change.wasAdded()) tagIdMap[change.elementAdded.id] = change.elementAdded
        })
    }

    private fun itemAdded(it: Item) {
        itemIdMap[it.id] = it
        if (it is FileItem) {
            files.add(it.file)
        }
    }

    private fun itemRemoved(it: Item) {
        itemIdMap.remove(it.id)
        if (it is FileItem) {
            files.remove(it.file)
            it.elementOf?.removeItem(it)
        }
        it.invalidate()

        _similarPairs.removeIf { p -> p.contains(it) } // Remove similar pairs containing removed item
    }


    private fun reserveItemID(): Int = nextItemID.getAndIncrement()

    private fun reserveTagID(): Int = nextTagID.getAndIncrement()

    fun reserveImportID(): Int = nextImportID.getAndIncrement()

    fun reserveImportTempGroupID(): Int = nextImportTempGroupID.getAndDecrement()

    fun getItem(id: Int): Item? = itemIdMap[id]

    fun hasItem(id: Int): Boolean = itemIdMap.containsKey(id)

    fun hasFile(file: File): Boolean = file in files

    fun addItem(item: Item): Boolean {
        if (item.id in itemIdMap) return false
        if (item is FileItem && item.file in files) return false

        _items.add(item)
        nextItemID.getAndUpdate { it.coerceAtLeast(item.id + 1) }
        return true
    }

    fun removeItem(item: Item): Boolean = _items.remove(item)

    fun getTag(id: Int): Tag? = tagIdMap[id]

    fun getTag(name: String): Tag? {
        val lName = name.lowercase()
        for (tag in tagIdMap.values) {
            if (tag.name == lName) return tag
        }
        return null
    }

    fun getOrMakeTag(name: String, color: String? = null, temporaryIfNew: Boolean = false): Tag {
        return getTag(name) ?: createTag(name, color, temporaryIfNew)
    }

    fun createTag(name: String, color: String? = null, temporary: Boolean = false): Tag {
        return Tag(reserveTagID(), name, this, color = color, temporary = temporary).also { addTag(it) }
    }

    fun hasTag(id: Int): Boolean = tagIdMap.containsKey(id)

    fun addTag(tag: Tag): Boolean {
        if (_tags.add(tag)) nextTagID.getAndUpdate { it.coerceAtLeast(tag.id + 1) }
        return true
    }

    fun removeTag(tag: Tag): Boolean {
        // TODO invalidate tag? Would need to check tags for invalidation anywhere they're being stored instead of instantly used, which I should probably already be doing.
        return _tags.remove(tag)
    }

    fun hasNonDupe(dupe: SimilarPair<Item>): Boolean = dupe in _knownNonDupes

    fun addNonDupe(dupe: SimilarPair<Item>): Boolean {
        return if (!hasNonDupe(dupe)) {
            _knownNonDupes.add(dupe)
            true
        } else {
            false
        }
    }

    fun removeNonDupe(dupe: SimilarPair<Item>): Boolean = _knownNonDupes.remove(dupe)

    fun addSimilarity(pair: SimilarPair<Item>): Boolean {
        if (pair in _similarPairs || pair in _knownNonDupes) return false
        return _similarPairs.add(pair)
    }

    fun removeSimilarity(pair: SimilarPair<Item>): Boolean = _similarPairs.remove(pair)

    fun purgeSimilarNonDupes() = _similarPairs.removeIf { p -> hasNonDupe(p) }

    fun createGroup(title: String): GroupItem {
        val g = GroupItem(reserveItemID(), System.currentTimeMillis(), this, title)
        g.addTag(getOrMakeTag("tagme"))
        addItem(g)
        return g
    }

    /**
    LONG BLOCKING CALL
     */
    fun createFileItem(file: File, skipAdding: Boolean): FileItem {
        if (hasFile(file)) throw IllegalArgumentException("File already present: ${file.path}")

        val id = reserveItemID()
        val bytes = Files.readAllBytes(file.toPath())
        val md5 = FileItem.bytesMD5(bytes)
        val added = System.currentTimeMillis()

        val item = when {
            ImageItem.isImage(file) -> {
                ImageItem(id, added, this, md5, file).apply {
                    val img = Image(bytes.inputStream())
                    cachedImage = SoftReference(img)
                    buildHistogram(img)
                }
            }
            VideoItem.isVideo(file) -> {
                VideoItem(id, added, this, md5, file)
            }
            else -> {
                FileItem(id, added, this, md5, file)
            }
        }

        item.addTag(getOrMakeTag("tagme"))
        if (!skipAdding) addItem(item)

        return item
    }

    fun findSimilarToSingle(item: FileItem): List<SimilarPair<Item>> {
        val similar = CPUDuplicateFinder.findDuplicates(
            listOf(item),
            items,
            similarityConfidence
        )

        similar.forEach { p -> addSimilarity(p) }
        return similar
    }

    fun addImport(job: Import): Boolean {
        if (job in _imports) return false
        _imports.add(job)
        nextImportID.getAndUpdate { it.coerceAtLeast(job.id + 1) }
        nextImportTempGroupID.getAndUpdate { it.coerceAtMost((job.group?.id?.value ?: 0) - 1) }

        return true
    }

    fun removeImport(job: Import): Boolean = _imports.remove(job)

}