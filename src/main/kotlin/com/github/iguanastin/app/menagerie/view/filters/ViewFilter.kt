package com.github.iguanastin.app.menagerie.view.filters

import com.github.iguanastin.app.menagerie.model.Item

abstract class ViewFilter(val exclude: Boolean) {

    abstract fun accepts(item: Item): Boolean

    protected fun invertIfExcluded(result: Boolean) = if (exclude) !result else result

}