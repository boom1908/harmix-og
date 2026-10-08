package com.boom.harmix.data.local

import com.boom.harmix.auth.UserSession
import com.boom.harmix.auth.UserSessionRepository
import com.boom.harmix.data.cloud.FirestoreLibraryRepository
import com.boom.harmix.data.local.dao.PlaylistDao
import com.boom.harmix.data.local.dao.SavedSongDao
import com.boom.harmix.data.local.entity.PlaylistEntity
import com.boom.harmix.data.local.entity.PlaylistSongCrossRef
import com.boom.harmix.data.local.entity.SavedSongEntity
import com.boom.harmix.extractor.StreamItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class PlaylistUi(
    val id: Long,
    val name: String,
    val songs: List<StreamItem>
)

@Singleton
class LibraryRepository @Inject constructor(
    private val savedSongDao: SavedSongDao,
    private val playlistDao: PlaylistDao,
    private val userSessionRepository: UserSessionRepository,
    private val firestoreLibraryRepository: FirestoreLibraryRepository
) {

    fun getSavedSongs(): Flow<List<StreamItem>> =
        userSessionRepository.session.flatMapLatest { session ->
            when (session) {
                UserSession.Guest -> savedSongDao.getAllSongs().map { list -> list.map { it.toStreamItem() } }
                is UserSession.Authenticated -> firestoreLibraryRepository.getSavedSongs(session.uid)
                else -> emptyFlow()
            }
        }

    fun getLikedSongs(): Flow<List<StreamItem>> =
        userSessionRepository.session.flatMapLatest { session ->
            when (session) {
                UserSession.Guest -> savedSongDao.getLikedSongs().map { list -> list.map { it.toStreamItem() } }
                is UserSession.Authenticated -> firestoreLibraryRepository.getLikedSongs(session.uid)
                else -> emptyFlow()
            }
        }

    fun getLikedUrls(): Flow<Set<String>> =
        userSessionRepository.session.flatMapLatest { session ->
            when (session) {
                UserSession.Guest -> savedSongDao.getLikedUrls().map { it.toSet() }
                is UserSession.Authenticated -> firestoreLibraryRepository.getLikedUrls(session.uid)
                else -> emptyFlow()
            }
        }

    suspend fun saveSong(item: StreamItem) {
        authenticatedUid()?.let {
            firestoreLibraryRepository.saveSong(it, item)
        } ?: savedSongDao.insertSong(item.toEntity(liked = true))
    }

    suspend fun removeSong(item: StreamItem) {
        authenticatedUid()?.let {
            firestoreLibraryRepository.removeSong(it, item)
        } ?: savedSongDao.deleteSongByUrl(item.url)
    }

    /** Heart toggle used by the player and track rows. */
    suspend fun toggleLike(item: StreamItem): Boolean {
        authenticatedUid()?.let {
            return firestoreLibraryRepository.toggleLike(it, item)
        }
        val alreadySaved = savedSongDao.isSongSaved(item.url)
        return if (!alreadySaved) {
            savedSongDao.insertSong(item.toEntity(liked = true))
            true
        } else {
            val liked = savedSongDao.isLiked(item.url)
            savedSongDao.setLiked(item.url, !liked)
            !liked
        }
    }

    suspend fun isSongSaved(url: String): Boolean =
        authenticatedUid()?.let { firestoreLibraryRepository.isSongSaved(it, url) }
            ?: savedSongDao.isSongSaved(url)

    fun getPlaylists(): Flow<List<PlaylistUi>> =
        userSessionRepository.session.flatMapLatest { session ->
            when (session) {
                UserSession.Guest -> playlistDao.getPlaylistsWithSongs().map { list ->
                    list.map { withSongs ->
                        PlaylistUi(
                            id = withSongs.playlist.playlistId,
                            name = withSongs.playlist.name,
                            songs = withSongs.songs.map { it.toStreamItem() }
                        )
                    }
                }
                is UserSession.Authenticated -> firestoreLibraryRepository.getPlaylists(session.uid)
                else -> emptyFlow()
            }
        }

    /** Single playlist with its songs in saved order. */
    fun getPlaylist(playlistId: Long): Flow<PlaylistUi?> =
        userSessionRepository.session.flatMapLatest { session ->
            when (session) {
                UserSession.Guest -> combine(
                    playlistDao.getPlaylist(playlistId),
                    playlistDao.getPlaylistSongs(playlistId)
                ) { playlist, songs ->
                    playlist?.let {
                        PlaylistUi(it.playlistId, it.name, songs.map { song -> song.toStreamItem() })
                    }
                }
                is UserSession.Authenticated -> firestoreLibraryRepository.getPlaylist(session.uid, playlistId)
                else -> emptyFlow()
            }
        }

    suspend fun createPlaylist(name: String): Long =
        authenticatedUid()?.let { firestoreLibraryRepository.createPlaylist(it, name) }
            ?: playlistDao.insertPlaylist(PlaylistEntity(name = name))

    /**
     * Merge target for a synced YouTube playlist: reuses the local playlist that was
     * created from the same remote id (or adopts a same-named local one) so re-syncing
     * never duplicates playlists.
     */
    suspend fun getOrCreateRemotePlaylist(remoteId: String, name: String): Long {
        authenticatedUid()?.let {
            return firestoreLibraryRepository.getOrCreateRemotePlaylist(it, remoteId, name)
        }
        val matches = playlistDao.findPlaylistsByRemoteId(remoteId)
        if (matches.isNotEmpty()) {
            // Older builds could already have duplicate remote rows. Keep the
            // oldest row as the canonical playlist and merge its songs before
            // removing the extras, so a later sync repairs the library too.
            val canonical = matches.first()
            matches.drop(1).forEach { duplicate ->
                playlistDao.getPlaylistSongRefs(duplicate.playlistId).forEach { ref ->
                    if (!playlistDao.isSongInPlaylist(canonical.playlistId, ref.songUrl)) {
                        playlistDao.addSongToPlaylist(
                            ref.copy(
                                playlistId = canonical.playlistId,
                                position = playlistDao.getNextPosition(canonical.playlistId)
                            )
                        )
                    }
                }
                playlistDao.deletePlaylist(duplicate.playlistId)
            }
            if (canonical.name != name) playlistDao.renamePlaylist(canonical.playlistId, name)
            return canonical.playlistId
        }
        val legacyName = name.removeSuffix(" (YouTube)")
        (playlistDao.findLocalPlaylistByName(name)
            ?: playlistDao.findLocalPlaylistByName(legacyName))?.let { adopted ->
            playlistDao.setRemoteId(adopted.playlistId, remoteId)
            return adopted.playlistId
        }
        return playlistDao.insertPlaylist(PlaylistEntity(name = name, remoteId = remoteId))
    }

    /** True when the song was newly added (false when it was already in the playlist). */
    suspend fun addSongToPlaylistIfAbsent(playlistId: Long, item: StreamItem): Boolean {
        authenticatedUid()?.let {
            return firestoreLibraryRepository.addSongToPlaylistIfAbsent(it, playlistId, item)
        }
        if (playlistDao.isSongInPlaylist(playlistId, item.url)) return false
        addSongToPlaylist(playlistId, item)
        return true
    }

    suspend fun renamePlaylist(playlistId: Long, name: String) {
        val uid = authenticatedUid()
        if (uid != null) {
            firestoreLibraryRepository.renamePlaylist(uid, playlistId, name)
        } else if (name.isNotBlank()) {
            playlistDao.renamePlaylist(playlistId, name)
        }
    }

    suspend fun addSongToPlaylist(playlistId: Long, item: StreamItem) {
        authenticatedUid()?.let {
            firestoreLibraryRepository.addSongToPlaylist(it, playlistId, item)
            return
        }
        savedSongDao.insertSongIfAbsent(item.toEntity())
        if (playlistDao.isSongInPlaylist(playlistId, item.url)) return
        val nextPosition = playlistDao.getNextPosition(playlistId)
        playlistDao.addSongToPlaylist(
            PlaylistSongCrossRef(playlistId = playlistId, songUrl = item.url, position = nextPosition)
        )
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songUrl: String) {
        authenticatedUid()?.let {
            firestoreLibraryRepository.removeSongFromPlaylist(it, playlistId, songUrl)
        } ?: playlistDao.removeSongFromPlaylist(playlistId, songUrl)
    }

    suspend fun deletePlaylist(playlistId: Long) {
        authenticatedUid()?.let {
            firestoreLibraryRepository.deletePlaylist(it, playlistId)
        } ?: playlistDao.deletePlaylist(playlistId)
    }

    private fun authenticatedUid(): String? =
        (userSessionRepository.session.value as? UserSession.Authenticated)?.uid
}

private fun SavedSongEntity.toStreamItem() = StreamItem(
    title = title,
    url = url,
    thumbnailUrl = thumbnailUrl,
    uploader = uploader
)

private fun StreamItem.toEntity(liked: Boolean = false) = SavedSongEntity(
    url = url,
    title = title,
    thumbnailUrl = thumbnailUrl,
    uploader = uploader,
    liked = liked
)
