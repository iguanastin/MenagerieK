package com.github.iguanastin.app.menagerie.duplicates.remote

import com.github.iguanastin.app.context.MenagerieContext
import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.view.runOnUIThread
import mu.KotlinLogging
import org.apache.http.client.HttpResponseException

private val log = KotlinLogging.logger {}

class AutoTagger(
    private val context: MenagerieContext,
    private val items: List<Item>,
    private val source: OnlineMatchFinder,
    private val finishedCheckingItem: (Item) -> Unit = {},
    private val onFinished: () -> Unit = {},
    private val onError: (Throwable) -> Unit = { log.error("Auto tagger error", it) }
) :
    Thread("Auto tagger") {

    @Volatile
    private var closed = false

    init {
        isDaemon = true
    }

    override fun run() {
        items.forEach { item ->
            if (closed) return

            val set = OnlineMatchSet(item)
            source.findMatches(set)
            set.error?.also {
                onError(it)
                finishedCheckingItem(item)
                return@forEach
            }

            val sourcesUsed = mutableSetOf<String>()

            set.matches.forEach { match ->
                if (closed) return
                @Suppress("LABEL_NAME_CLASH")
                if (!SourceTagParser.canGetTagsFrom(match.sourceUrl)) return@forEach

                // Only get tags from source if it hasn't been searched for this item yet
                val sourceName =
                    match.sourceUrl.substring(0, match.sourceUrl.indexOf("/", match.sourceUrl.indexOf("//") + 2))
                @Suppress("LABEL_NAME_CLASH")

                try {
                    val tags = SourceTagParser.getTags(match.sourceUrl, source.client!!)
                    if (closed) return
                    if (tags.isEmpty()) return@forEach

                    if (sourcesUsed.contains(sourceName)) return@forEach
                    sourcesUsed.add(sourceName)

                    runOnUIThread {
                        tags.forEach { tag ->
                            item.addTag(item.menagerie.getOrMakeTag(context.prefs.tags.tagAliases.apply(tag), temporaryIfNew = true))
                        }
                    }
                } catch (e: HttpResponseException) {
                    onError(e)
                }
            }

            finishedCheckingItem(item)
        }

        onFinished()
    }

    fun close() {
        closed = true
    }

}