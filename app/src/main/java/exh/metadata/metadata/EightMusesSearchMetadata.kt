package exh.metadata.metadata

import android.content.Context
import eu.kanade.tachiyomi.R
import exh.metadata.metadata.base.RaisedSearchMetadata
import kotlinx.serialization.Serializable
import tachiyomi.source.model.MangaInfo

@Serializable
class EightMusesSearchMetadata : RaisedSearchMetadata() {
    var path: List<String> = emptyList()

    var title by titleDelegate(TITLE_TYPE_MAIN)

    var thumbnailUrl: String? = null

    override fun createMangaInfo(manga: MangaInfo): MangaInfo {
        val key = path.joinToString("/", prefix = "/")

        val title = title

        val cover = thumbnailUrl

        val artist = tags.ofNamespace(ARTIST_NAMESPACE).joinToString { it.name }

        val genres = tagsToGenreList()

        val description = "meta"

        return manga.copy(
            key = key,
            title = title ?: manga.title,
            cover = cover ?: manga.cover,
            artist = artist,
            genres = genres,
            description = description
        )
    }

    override fun getExtraInfoPairs(context: Context): List<Pair<String, String>> {
        val pairs = mutableListOf<Pair<String, String>>()
        title?.let { pairs += context.getString(R.string.title) to it }
        val path = path.joinToString("/", prefix = "/")
        if (path.isNotBlank()) {
            pairs += context.getString(R.string.path) to path
        }
        thumbnailUrl?.let { pairs += context.getString(R.string.thumbnail_url) to it }
        return pairs
    }

    companion object {
        private const val TITLE_TYPE_MAIN = 0

        const val TAG_TYPE_DEFAULT = 0

        const val TAGS_NAMESPACE = "tags"
        const val ARTIST_NAMESPACE = "artist"
    }
}
