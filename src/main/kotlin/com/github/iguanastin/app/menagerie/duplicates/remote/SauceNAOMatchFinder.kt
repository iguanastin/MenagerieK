package com.github.iguanastin.app.menagerie.duplicates.remote

import com.github.iguanastin.app.menagerie.model.FileItem
import com.github.iguanastin.app.menagerie.model.ImageItem
import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.app.menagerie.model.Thumbnail
import javafx.embed.swing.SwingFXUtils
import mu.KotlinLogging
import org.apache.http.client.HttpResponseException
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.mime.FormBodyPartBuilder
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.entity.mime.content.InputStreamBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.util.concurrent.CountDownLatch
import java.util.stream.Collectors
import javax.imageio.ImageIO

private val log = KotlinLogging.logger {}

class SauceNAOMatchFinder : OnlineMatchFinder() {

    private val url = "https://saucenao.com"

    private var lastSearch: Long = 0
    private val cooldown: Long = 30000
    private val perCooldown = 3
    private var searchesSinceLastCooldown = 0


    override fun findMatches(set: OnlineMatchSet) {
        try {
            set.reset()
            set.state = OnlineMatchSet.State.LOADING

            checkCooldown()

            val doc = post(set.item as FileItem)
            if (doc == null) {
                set.state = OnlineMatchSet.State.FINISHED
                return
            }
            val matches = findMatchesInDocument(doc)

            set.matches = matches
            set.state = OnlineMatchSet.State.FINISHED
        } catch (t: Throwable) {
            log.error("Exception while finding matches", t)
            set.error = t
            set.state = OnlineMatchSet.State.FAILED
        }
    }

    private fun checkCooldown() {
        // Sleep if it there are no searches available
        if (searchesSinceLastCooldown >= perCooldown) {
            Thread.sleep((cooldown - (System.currentTimeMillis() - lastSearch)).coerceAtLeast(0))
            searchesSinceLastCooldown = 0
        }
        searchesSinceLastCooldown++
        lastSearch = System.currentTimeMillis()
    }

    private fun findMatchesInDocument(doc: Document): List<OnlineMatch> {
        val matches = mutableListOf<OnlineMatch>()

        for (element in doc.select("div#middle > div.result:not(.hidden)")) {
            if (element.id() == "result-hidden-notification") continue

            val thumbUrl = element.select("div.resultimage img").first()!!.attr("src")
            val details = element.select("div.resultsimilarityinfo").first()!!.ownText()
            val sources = mutableListOf<String>()

            // Misc source (gelbooru/danbooru)
            for (misc in element.select("div.resultmiscinfo > a")) {
                val source = fixLink(misc.attr("href"))
                if (!sources.contains(source)) sources.add(source)
            }
            // Pixiv/Generic source
            for (strong in element.select("div.resultcontentcolumn > strong")) {
                if (strong.ownText() in arrayOf("Pixiv ID:", "Source:")) {
                    val source = fixLink(strong.nextElementSibling()!!.attr("href"))
                    if (!sources.contains(source)) sources.add(source)
                }
            }

            for (source in sources) {
                val sourceName = if (source.matches(Regex("^https?://.*"))) source.substringAfter("://").substringBefore('/') else source.substringBefore('/')

                var add = true
                for (match in matches) {
                    if (match.sourceUrl == source) {
                        add = false
                        break
                    }
                }
                if (add) matches.add(OnlineMatch(source, thumbUrl, sourceName, details))
            }
        }

        return matches
    }

    private fun fixLink(url: String): String {
        return if (url.startsWith("//")) {
            "https:$url"
        } else {
            url
        }
    }

    private fun post(item: FileItem): Document? {
        if (item.file.extension.lowercase() !in ImageItem.fileExtensions) throw IllegalArgumentException("Cannot process this type of file")
        val img = acquireThumbnail(item) ?: return null

        ByteArrayOutputStream().use { baos ->
            ImageIO.write(img, item.file.extension, baos)

            val post = HttpPost("$url/search.php")
            ByteArrayInputStream(baos.toByteArray()).use { bais ->
                post.entity = MultipartEntityBuilder.create().addPart(FormBodyPartBuilder.create("file", InputStreamBody(bais, item.file.name)).build()).build()
            }

            client!!.execute(post).use { response ->
                if (response.statusLine.statusCode == 200) {
                    response.entity.content.use { content ->
                        val result = BufferedReader(InputStreamReader(content)).lines().collect(Collectors.joining("\n"))
                        return Jsoup.parse(result)
                    }
                } else {
                    throw HttpResponseException(response.statusLine.statusCode, "Bad response code")
                }
            }
        }
    }

    private fun acquireThumbnail(item: FileItem): BufferedImage? {
        val latch = CountDownLatch(1)
        var thumb: Thumbnail? = null
        item.getThumbnail().want(this) {
            thumb = it
            latch.countDown()
        }
        latch.await()

        return processThumbnail(thumb!!)
    }

    private fun processThumbnail(thumb: Thumbnail): BufferedImage? {
        if (thumb.item is FileItem && thumb.item.file.extension.equals("gif", true)) {
            val img: BufferedImage = ImageIO.read(thumb.item.file)
            var width = img.width
            var height = img.height
            if (width > Item.thumbnailSize) {
                height = (height * Item.thumbnailSize / width).toInt()
                width = Item.thumbnailSize.toInt()
            }
            if (height > Item.thumbnailSize) {
                width = (width * Item.thumbnailSize / height).toInt()
                height = Item.thumbnailSize.toInt()
            }
            val image = BufferedImage(width, height, img.type)
            val g: Graphics2D = image.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.drawImage(img, 0, 0, width, height, 0, 0, img.width, img.height, null)
            g.dispose()

            return image
        } else {
            return SwingFXUtils.fromFXImage(thumb.image, null)
        }
    }

}