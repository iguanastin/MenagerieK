package com.github.iguanastin.app.menagerie.duplicates.remote

import com.github.iguanastin.app.menagerie.model.Item

class AutoTagger(private val items: List<Item>, private val source: OnlineMatchFinder, private val onFoundTagsForItem: (Item) -> Unit = {}, private val onFinished: () -> Unit = {}) :
    Thread("Auto tagger") {

    init {
        isDaemon = true
    }

    override fun run() {
        items.forEach { item ->
            val set = OnlineMatchSet(item)
            source.findMatches(set)

            set.matches.forEach { match ->
                val tags = SourceTagParser.getTags(match.sourceUrl, source.client!!)
                tags.forEach { tag ->
                    item.addTag(item.menagerie.getOrMakeTag(tag, true))
                }
            }

            onFoundTagsForItem(item)
        }

        onFinished()
    }

}