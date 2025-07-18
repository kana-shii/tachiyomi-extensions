package eu.kanade.tachiyomi.extension.all.mangapark

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import kotlin.text.replace
import kotlin.text.trim

typealias SearchResponse = Data<SearchComics>
typealias DetailsResponse = Data<ComicNode>
typealias ChapterListResponse = Data<ChapterList>
typealias PageListResponse = Data<ChapterPages>

@Serializable
class Data<T>(val data: T)

@Serializable
class Items<T>(val items: List<T>)

@Serializable
class SearchComics(
    @SerialName("get_searchComic") val searchComics: Items<Data<MangaParkComic>>,
)

@Serializable
class ComicNode(
    @SerialName("get_comicNode") val comic: Data<MangaParkComic>,
)

@Serializable
class MangaParkComic(
    private val id: String,
    private val name: String,
    private val altNames: List<String>? = null,
    private val authors: List<String>? = null,
    private val artists: List<String>? = null,
    private val genres: List<String>? = null,
    private val originalStatus: String? = null,
    private val uploadStatus: String? = null,
    private val summary: String? = null,
    private val extraInfo: String? = null,
    @SerialName("urlCoverOri") private val cover: String? = null,
    private val urlPath: String,
    @SerialName("max_chapterNode") private val latestChapter: Data<ImageFiles>? = null,
    @SerialName("first_chapterNode") private val firstChapter: Data<ImageFiles>? = null,
) {
    fun toSManga(shortenTitle: Boolean, pageAsCover: String, customTitleRegex: Regex) = SManga.create().apply {
        url = "$urlPath#$id"
        title = if (shortenTitle) {
            var shortName = name
            while (shortenTitleRegex.containsMatchIn(shortName)) { // Use the Regex from companion object.
                shortName = shortName.replace(shortenTitleRegex, "").trim() // Use the Regex from companion object.
            }
            if (customTitleRegex.pattern.isNotEmpty()) {
                shortName = shortName.replace(customTitleRegex, "").trim()
            }

            shortName
        } else {
            if (customTitleRegex.pattern.isNotEmpty()) {
                name.replace(customTitleRegex, "").trim()
            } else {
                name
            }
        }
        thumbnail_url = run {
            val coverUrl = cover?.let {
                when {
                    it.startsWith("http") -> it
                    it.startsWith("/") -> "https://$THUMBNAIL_LOOPBACK_HOST$it"
                    else -> null
                }
            }

            if (pageAsCover != "off" && useLatestPageAsCover(genres)) {
                if (pageAsCover == "first") {
                    firstChapter?.data?.imageFile?.urlList?.firstOrNull() ?: coverUrl
                } else {
                    latestChapter?.data?.imageFile?.urlList?.firstOrNull() ?: coverUrl
                }
            } else {
                coverUrl
            }
        }
        author = authors?.joinToString()
        artist = artists?.joinToString()
        description = buildString {
            summary?.also {
                append(Jsoup.parse(it).wholeText().trim())
                append("\n\n")
            }
            extraInfo?.takeUnless(String::isBlank)?.also {
                append("\n\n----\n#### **Extra Info**\n")
                append(Jsoup.parse(it).wholeText().trim())
                append("\n\n")
            }
            altNames?.takeUnless(List<String>::isEmpty)
                ?.joinToString(
                    prefix = "\n\n----\n#### **Alternative Titles**\n",
                    separator = "\n",
                ) { "- ${it.trim()}" }
                ?.also(::append)

            val matches = mutableListOf<String>() // Store the matched strings directly

            if (shortenTitle) {
                val tempTitle = if (shortenTitleRegex.containsMatchIn(name)) {
                    var shortName = name
                    while (shortenTitleRegex.containsMatchIn(shortName)) {
                        val match = shortenTitleRegex.find(shortName)!!
                        matches.add(match.value) // Store match.value
                        shortName = shortName.replace(match.value, "").trim()
                    }
                    shortName
                } else {
                    name
                }

                if (customTitleRegex.pattern.isNotEmpty()) {
                    customTitleRegex.findAll(tempTitle).forEach { matchResult ->
                        matches.add(matchResult.value)
                    }
                }
            } else {
                if (customTitleRegex.pattern.isNotEmpty()) {
                    customTitleRegex.findAll(name).forEach { matchResult ->
                        matches.add(matchResult.value)
                    }
                }
            }

            if (matches.isNotEmpty()) {
                append("\n\n----\n#### **Removed from title**\n")
                matches.forEach { match ->
                    append("- `$match`\n")
                }
            }
        }.trim()
        genre = genres?.joinToString { it.replace("_", " ").toCamelCase() }
        status = when (originalStatus ?: uploadStatus) {
            "ongoing" -> SManga.ONGOING
            "completed" -> {
                if (uploadStatus == "ongoing") {
                    SManga.PUBLISHING_FINISHED
                } else {
                    SManga.COMPLETED
                }
            }
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        initialized = true
    }

    companion object {
        private fun String.toCamelCase(): String {
            val result = StringBuilder(length)
            var capitalize = true
            for (char in this) {
                result.append(
                    if (capitalize) {
                        char.uppercase()
                    } else {
                        char.lowercase()
                    },
                )
                capitalize = char.isWhitespace()
            }
            return result.toString()
        }

        private fun useLatestPageAsCover(genres: List<String>?): Boolean {
            return genres.orEmpty().let {
                it.contains("hentai") && !it.contains("webtoon")
            }
        }

        private val shortenTitleRegex = Regex("\\([^()]*\\)|\\{[^{}]*\\}|\\[(?:(?!]).)*]|«[^»]*»|〘[^〙]*〙|「[^」]*」|『[^』]*』|≪[^≫]*≫|﹛[^﹜]*﹜|〖[^〖〗]*〗|𖤍.+?𖤍|《[^》]*》|⌜.+?⌝|⟨[^⟩]*⟩|【[^】]*】|([|].*)|([/].*)|([~].*)|-[^-]*-|‹[^›]*›", RegexOption.IGNORE_CASE)
    }
}

@Serializable
class ChapterList(
    @SerialName("get_comicChapterList") val chapterList: List<Data<MangaParkChapter>>,
)

@Serializable
class MangaParkChapter(
    private val id: String,
    @SerialName("dname") private val displayName: String,
    private val title: String? = null,
    private val dateCreate: Long? = null,
    private val dateModify: Long? = null,
    private val urlPath: String,
    private val srcTitle: String? = null,
    private val userNode: Data<Name>? = null,
    val dupChapters: List<Data<MangaParkChapter>> = emptyList(),
) {
    fun toSChapter() = SChapter.create().apply {
        url = "$urlPath#$id"
        name = buildString {
            append(displayName)
            title?.let { append(": ", it) }
        }
        date_upload = dateModify ?: dateCreate ?: 0L
        scanlator = userNode?.data?.name ?: srcTitle ?: "Unknown"
    }
}

@Serializable
class Name(val name: String)

@Serializable
class ChapterPages(
    @SerialName("get_chapterNode") val chapterPages: Data<ImageFiles>,
)

@Serializable
class ImageFiles(
    val imageFile: UrlList,
)

@Serializable
class UrlList(
    val urlList: List<String>,
)
