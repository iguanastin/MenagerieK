package com.github.iguanastin.app.menagerie.search.filters

import com.github.iguanastin.app.menagerie.model.Item

abstract class SearchFilter(val exclude: Boolean) {

    abstract fun accepts(item: Item): Boolean

    protected fun invertIfExcluded(result: Boolean) = if (exclude) !result else result

}