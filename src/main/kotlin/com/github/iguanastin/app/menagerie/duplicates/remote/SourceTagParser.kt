package com.github.iguanastin.app.menagerie.duplicates.remote

import org.apache.http.client.HttpResponseException
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.CloseableHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.stream.Collectors

object SourceTagParser {

    private val gelRegex = Regex("^https://gelbooru\\.com/.+page=post.+")
    private val danRegex = Regex("^https://danbooru\\.donmai\\.us/posts?/.+")
    private val yanRegex = Regex("^https://yande\\.re/post/.+")

    private const val artistPrefix = "a:"
    private const val characterPrefix = "c:"
    private const val copyrightPrefix = "s:"


    fun canGetTagsFrom(url: String): Boolean {
        return gelRegex.matches(url) || danRegex.matches(url) || yanRegex.matches(url)
    }

    fun getTags(url: String, client: CloseableHttpClient): List<String> {
        if (!canGetTagsFrom(url)) return emptyList()

        return if (gelRegex.matches(url)) {
            parseGelTags(get(url, client))
        } else if (danRegex.matches(url)) {
            parseDanTags(get(url, client))
        } else if (yanRegex.matches(url)) {
            parseYanTags(get(url, client))
        } else {
            emptyList()
        }
    }

    private fun parseYanTags(doc: Document): List<String> {
        if (doc.selectFirst("#image") == null) return emptyList()

        val tags = mutableListOf<String>()
        doc.apply {
            select(".tag-type-artist > a:nth-child(2)").forEach {
                tags.add("$artistPrefix${it.ownText().replace(" ", "_")}")
                tags.add(it.ownText().replace(" ", "_"))
            }
            select(".tag-type-character > a:nth-child(2)").forEach {
                tags.add("$characterPrefix${it.ownText().replace(" ", "_")}")
                tags.add(it.ownText().replace(" ", "_"))
            }
            select(".tag-type-copyright > a:nth-child(2)").forEach {
                tags.add("$copyrightPrefix${it.ownText().replace(" ", "_")}")
                tags.add(it.ownText().replace(" ", "_"))
            }
            select(".tag-type-general > a:nth-child(2)").forEach {
                tags.add(it.ownText().replace(" ", "_"))
            }
        }
        return tags
    }

    private fun parseGelTags(doc: Document): List<String> {
        if (doc.selectFirst("div.mainBodyPadding") == null) return emptyList()

        val tags = mutableListOf<String>()
        doc.apply {
            select(".tag-type-artist > a").forEach {
                tags.add("$artistPrefix${it.ownText().replace(" ", "_")}")
                tags.add(it.ownText().replace(" ", "_"))
            }
            select(".tag-type-character > a").forEach {
                tags.add("$characterPrefix${it.ownText().replace(" ", "_")}")
                tags.add(it.ownText().replace(" ", "_"))
            }
            select(".tag-type-copyright > a").forEach {
                tags.add("$copyrightPrefix${it.ownText().replace(" ", "_")}")
                tags.add(it.ownText().replace(" ", "_"))
            }
            select(".tag-type-general > a").forEach {
                tags.add(it.ownText().replace(" ", "_"))
            }
            select(".tag-type-metadata > a").forEach {
                tags.add(it.ownText().replace(" ", "_"))
            }
        }
        return tags
    }

    private fun parseDanTags(doc: Document): List<String> {
        if (doc.selectFirst("section.image-container") == null) return emptyList()

        val tags = mutableListOf<String>()
        doc.apply {
            select(".artist-tag-list .search-tag").forEach {
                tags.add("$artistPrefix${it.ownText().replace(" ", "_")}")
                tags.add(it.ownText().replace(" ", "_"))
            }
            select(".character-tag-list .search-tag").forEach {
                tags.add("$characterPrefix${it.ownText().replace(" ", "_")}")
                tags.add(it.ownText().replace(" ", "_"))
            }
            select(".copyright-tag-list .seach-tag").forEach {
                tags.add("$copyrightPrefix${it.ownText().replace(" ", "_")}")
                tags.add(it.ownText().replace(" ", "_"))
            }
            select(".general-tag-list .search-tag").forEach {
                tags.add(it.ownText().replace(" ", "_"))
            }
            select(".meta-tag-list .search-tag").forEach {
                tags.add(it.ownText().replace(" ", "_"))
            }
        }
        return tags
    }

    private fun get(url: String, client: CloseableHttpClient): Document {
        val get = HttpGet(url)
        client.execute(get).use { response ->
            if (response.statusLine.statusCode != 200) {
                throw HttpResponseException(response.statusLine.statusCode, response.statusLine.reasonPhrase)
            }

            response.entity.content.use { content ->
                val result = BufferedReader(InputStreamReader(content)).lines().collect(Collectors.joining("\n"))
                return Jsoup.parse(result)
            }
        }
    }

}