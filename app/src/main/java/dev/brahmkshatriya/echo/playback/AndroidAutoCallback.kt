package dev.brahmkshatriya.echo.playback

import android.content.ContentResolver
import android.content.Context
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import androidx.core.net.toUri
import androidx.media.MediaBrowserServiceCompat
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.common.Extension
import dev.brahmkshatriya.echo.common.MusicExtension
import dev.brahmkshatriya.echo.common.clients.AlbumClient
import dev.brahmkshatriya.echo.common.clients.ArtistClient
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.HomeFeedClient
import dev.brahmkshatriya.echo.common.clients.LibraryFeedClient
import dev.brahmkshatriya.echo.common.clients.PlaylistClient
import dev.brahmkshatriya.echo.common.clients.RadioClient
import dev.brahmkshatriya.echo.common.clients.SearchFeedClient
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.ImageHolder
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Radio
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.di.App
import dev.brahmkshatriya.echo.download.Downloader
import dev.brahmkshatriya.echo.extensions.ExtensionUtils.isClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.WeakHashMap

/**
 * MediaBrowserServiceCompat browse tree, mirroring the original media3
 * MediaLibraryService tree: root / extension nodes, then album / playlist /
 * radio / artist / lists / shelf branches per extension.
 */
abstract class AndroidAutoCallback(
    val app: App,
    val scope: CoroutineScope,
    val extensionList: StateFlow<List<MusicExtension>>,
    val downloadFlow: StateFlow<List<Downloader.Info>>
) {

    val context get() = app.context

    fun root() = MediaBrowserServiceCompat.BrowserRoot(ROOT, null)

    /** Async children of a browse node; the caller detaches the result first. */
    fun loadChildren(
        parentId: String, options: Bundle?,
        result: MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>,
    ) {
        result.detach()
        scope.launch {
            val page = options.page()
            runCatching { getChildren(parentId, page) }
                .onSuccess(result::sendResult)
                .onFailure { it.printStackTrace(); result.sendBrowseError() }
        }
    }

    fun search(
        query: String,
        result: MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>,
    ) {
        result.detach()
        scope.launch {
            val extensions = extensionList.value
            extensions.forEach { searchNodes["$ROOT/${it.id}/$SEARCH"] = query }
            result.sendResult(extensions.map { ext ->
                browsableItem("$ROOT/${ext.id}/$SEARCH", ext.name, query)
            })
        }
    }

    private fun Bundle?.page() = this?.getInt(MediaBrowserCompat.EXTRA_PAGE, 1)?.takeIf { it > 0 } ?: 1

    private suspend fun getChildren(
        parentId: String, page: Int,
    ): List<MediaBrowserCompat.MediaItem> {
        val extensions = extensionList.value
        if (parentId == ROOT) return extensions.map { extensionNode(it, context) }
        val extId = parentId.substringAfter("$ROOT/").substringBefore("/")
        val extension = extensions.first { it.id == extId }
        val type = parentId.substringAfter("$extId/").substringBefore("/")
        return when (type) {
            ALBUM -> extension.getList<AlbumClient> {
                val id = parentId.substringAfter("$ALBUM/").substringBefore("/")
                val unloaded = itemMap.get(id) as Album
                getTracks(context, extId, id, page) {
                    val album = loadAlbum(unloaded)
                    album to loadTracks(album)
                }
            }

            PLAYLIST -> extension.getList<PlaylistClient> {
                val id = parentId.substringAfter("$PLAYLIST/").substringBefore("/")
                val unloaded = itemMap.get(id) as Playlist
                getTracks(context, extId, id, page) {
                    val playlist = loadPlaylist(unloaded)
                    playlist to loadTracks(playlist)
                }
            }

            RADIO -> extension.getList<RadioClient> {
                val id = parentId.substringAfter("$RADIO/").substringBefore("/")
                val radio = itemMap.get(id) as Radio
                getTracks(context, extId, id, page) {
                    radio to loadTracks(radio)
                }
            }

            ARTIST -> extension.getList<ArtistClient> {
                val id = parentId.substringAfter("$ARTIST/").substringBefore("/")
                val artist = loadArtist(Artist(id, ""))
                loadFeed(artist).toMediaItems(artist.id, context, extId, page)
            }

            LIST -> extension.getList<ExtensionClient> {
                val id = parentId.substringAfter("$LIST/").substringBefore("/")
                getListsItems(context, id, extId)
            }

            SHELF -> extension.getList<ExtensionClient> {
                val id = parentId.substringAfter("$SHELF/").substringBefore("/")
                getShelfItems(context, id, extId, page)
            }

            else -> companion.extensionShellNodes(extension, context, extId)
        }
    }

    companion object {

        private const val ROOT = "root"
        private const val LIBRARY = "library"
        private const val HOME = "home"
        private const val SEARCH = "search"
        private const val FEED = "feed"
        private const val SHELF = "shelf"
        private const val LIST = "list"

        private const val ARTIST = "artist"
        private const val USER = "user"
        private const val ALBUM = "album"
        private const val PLAYLIST = "playlist"
        private const val RADIO = "radio"

        private val searchNodes = WeakHashMap<String, String>()

        private class BrowseException(cause: Throwable) : Exception(cause)

        private fun MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>.sendBrowseError() =
            sendError(Bundle())

        fun browsableItem(
            id: String,
            title: String,
            subtitle: String? = null,
            browsable: Boolean = true,
            artWorkUri: Uri? = null,
        ) = MediaBrowserCompat.MediaItem(
            MediaDescriptionCompat.Builder()
                .setMediaId(id)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setIconUri(artWorkUri)
                .build(),
            if (browsable) MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
            else MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )

        private fun Resources.getUri(int: Int): Uri {
            val scheme = ContentResolver.SCHEME_ANDROID_RESOURCE
            val pkg = getResourcePackageName(int)
            val type = getResourceTypeName(int)
            val name = getResourceEntryName(int)
            return "$scheme://$pkg/$type/$name".toUri()
        }

        private fun ImageHolder.toUri(context: Context) = when (this) {
            is ImageHolder.ResourceUriImageHolder -> uri.toUri()
            is ImageHolder.NetworkRequestImageHolder -> request.url.toUri()
            is ImageHolder.ResourceIdImageHolder -> context.resources.getUri(resId)
            is ImageHolder.HexColorImageHolder -> "".toUri()
        }

        fun extensionNode(
            extension: MusicExtension, context: Context
        ): MediaBrowserCompat.MediaItem = browsableItem(
            "$ROOT/${extension.id}", extension.name,
            context.getString(R.string.extension),
            extension.instance.value().isSuccess,
            extension.metadata.icon?.toUri(context)
        )

        private fun extensionShellNodes(
            extension: MusicExtension, context: Context, extId: String,
        ): List<MediaBrowserCompat.MediaItem> = listOfNotNull(
            if (extension.isClient<HomeFeedClient>())
                browsableItem("$ROOT/$extId/$HOME", context.getString(R.string.home))
            else null,
            if (extension.isClient<SearchFeedClient>())
                browsableItem("$ROOT/$extId/$SEARCH", context.getString(R.string.search))
            else null,
            if (extension.isClient<LibraryFeedClient>())
                browsableItem("$ROOT/$extId/$LIBRARY", context.getString(R.string.library))
            else null,
        )

        suspend inline fun <reified C> Extension<*>.getList(
            block: C.() -> List<MediaBrowserCompat.MediaItem>
        ): List<MediaBrowserCompat.MediaItem> = runCatching {
            val client = instance.value().getOrThrow() as? C ?: error("not supported")
            client.block()
        }.getOrElse {
            it.printStackTrace()
            throw BrowseException(it)
        }

        private val itemMap = WeakHashMap<String, EchoMediaItem>()
        private fun EchoMediaItem.toMediaItem(
            context: Context, extId: String
        ): MediaBrowserCompat.MediaItem = when (this) {
            is Track -> toItem(context, extId)
            else -> {
                val id = hashCode().toString()
                itemMap[id] = this
                val page = when (this) {
                    is Artist, is Radio -> USER
                    is Album -> ALBUM
                    is Playlist -> PLAYLIST
                    else -> throw IllegalStateException("Invalid type")
                }
                browsableItem(
                    "$ROOT/$extId/$page/$id",
                    title,
                    subtitleWithE,
                    true,
                    cover?.toUri(context)
                )
            }
        }

        private val listsMap = WeakHashMap<String, Shelf.Lists<*>>()
        private fun getListsItems(
            context: Context, id: String, extId: String
        ) = run {
            val shelf = listsMap[id]!!
            when (shelf) {
                is Shelf.Lists.Categories -> shelf.list.map { it.toMediaItem(context, extId) }
                is Shelf.Lists.Items -> shelf.list.map { it.toMediaItem(context, extId) }
                is Shelf.Lists.Tracks -> shelf.list.map { it.toItem(context, extId) }
            } + listOfNotNull(
                if (shelf.more != null) {
                    val moreId = shelf.id
                    feedMap[moreId] = shelf.more
                    browsableItem(
                        "$ROOT/$extId/$FEED/$moreId",
                        context.getString(R.string.more)
                    )
                } else null
            )
        }

        private fun Shelf.toMediaItem(
            context: Context, extId: String
        ): MediaBrowserCompat.MediaItem = when (this) {
            is Shelf.Category -> {
                val items = feed
                if (items != null) feedMap[id] = items
                browsableItem("$ROOT/$extId/$FEED/$id", title, subtitle, items != null)
            }

            is Shelf.Item -> media.toMediaItem(context, extId)
            is Shelf.Lists<*> -> {
                val id = "${id.hashCode()}"
                listsMap[id] = this
                browsableItem("$ROOT/$extId/$LIST/$id", title, subtitle)
            }
        }

        private val shelvesMap = WeakHashMap<String, PagedData<Shelf>>()
        private val continuations = WeakHashMap<Pair<String, Int>, String?>()
        private suspend fun getShelfItems(
            context: Context, id: String, extId: String, page: Int
        ): List<MediaBrowserCompat.MediaItem> {
            val shelf = shelvesMap[id]!!
            val (list, next) = shelf.loadPage(continuations[id to page])
            continuations[id to page + 1] = next
            return listOfNotNull(
                *list.map { it.toMediaItem(context, extId) }.toTypedArray()
            )
        }

        private val feedMap = WeakHashMap<String, Feed<Shelf>>()
        private suspend fun Feed<Shelf>.toMediaItems(
            id: String, context: Context, extId: String, page: Int
        ): List<MediaBrowserCompat.MediaItem> {
            val id = "${id.hashCode()}"
            feedMap[id] = this
            //TODO
            return listOf()
        }

        private val tracksMap = WeakHashMap<String, Pair<EchoMediaItem, PagedData<Track>>>()
        private suspend fun getTracks(
            context: Context,
            extId: String,
            id: String,
            page: Int,
            getTracks: suspend () -> Pair<EchoMediaItem, Feed<Track>?>
        ): List<MediaBrowserCompat.MediaItem> {
            val (item, tracks) = tracksMap.getOrPut(id) {
                val tracks = getTracks().run {
                    first to (second?.run { getPagedData(tabs.firstOrNull()) }?.pagedData
                        ?: PagedData.empty())
                }
                tracksMap[id] = tracks
                tracks
            }
            val (list, next) = tracks.loadPage(continuations[id to page])
            continuations[id to page + 1] = next
            return list.map { it.toItem(context, extId, item) }
        }

        private fun Track.toItem(
            context: Context, extensionId: String, con: EchoMediaItem? = null
        ): MediaBrowserCompat.MediaItem {
            context.saveToCache(id, Triple(this, extensionId, con), "auto")
            return MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder()
                    .setMediaId("auto/$id")
                    .setTitle(title)
                    .setSubtitle(subtitleWithE)
                    .setIconUri(cover?.toUri(context))
                    .build(),
                MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
            )
        }
    }
}
