package com.github.iguanastin.app.menagerie.model

import java.io.File

class Change<T>(val old: T, val new: T)

open class ItemChangeBase(val item: Item)

open class ItemChange(item: Item, val tagsAdded: List<Tag>? = null, val tagsRemoved: List<Tag>? = null): ItemChangeBase(item)

open class FileItemChange(item: Item, val md5: Change<String>? = null, val file: Change<File>? = null, val elementOf: Change<GroupItem?>? = null) : ItemChangeBase(item)

open class ImageItemChange(item: Item, val noSimilar: Change<Boolean>? = null, val histogram: Change<Histogram?>? = null) : ItemChangeBase(item)

open class GroupItemChange(item: Item, val title: Change<String>? = null, val items: List<FileItem>? = null) : ItemChangeBase(item)
