package com.github.iguanastin.app.menagerie.duplicates.remote

import com.github.iguanastin.app.menagerie.model.Item
import mu.KotlinLogging
import org.apache.http.client.HttpResponseException

private val log = KotlinLogging.logger {}

class AutoTagger(
    private val items: List<Item>,
    private val source: OnlineMatchFinder,
    private val onFoundTagsForItem: (Item) -> Unit = {},
    private val onFinished: () -> Unit = {}
) :
    Thread("Auto tagger") {

    init {
        isDaemon = true
    }

    override fun run() {
        items.forEach { item ->
            val set = OnlineMatchSet(item)
            source.findMatches(set)

            set.matches.forEach { match ->
                try {
                    val tags = SourceTagParser.getTags(match.sourceUrl, source.client!!)
                    tags.forEach { tag ->
                        item.addTag(item.menagerie.getOrMakeTag(tag, true))
                    }
                } catch (e: HttpResponseException) {
                    log.error("Bad response from source", e)
                }
            }

            onFoundTagsForItem(item)
        }

        onFinished()
    }

}