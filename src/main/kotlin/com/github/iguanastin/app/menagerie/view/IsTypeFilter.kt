package com.github.iguanastin.app.menagerie.view

import com.github.iguanastin.app.menagerie.model.*

class IsTypeFilter(val type: Type, exclude: Boolean): ViewFilter(exclude) {

    enum class Type {
        Image,
        Video,
        Group,
        File
    }

    override fun accepts(item: Item): Boolean {
        return when (type) {
            Type.Image -> item is ImageItem
            Type.Video -> item is VideoItem
            Type.Group -> item is GroupItem
            Type.File -> item is FileItem
        }
    }

}