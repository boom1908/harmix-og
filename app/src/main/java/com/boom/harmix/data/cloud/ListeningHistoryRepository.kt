package com.boom.harmix.data.cloud

import com.boom.harmix.extractor.StreamItem
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class ListeningHistoryEntry(
    val id: String,
    val song: StreamItem,
    val playedAtMs: Long?
)

@Singleton
class ListeningHistoryRepository @Inject constructor() {
    private val firestore = FirebaseFirestore.getInstance()

    fun getRecentHistory(uid: String, limit: Long = 100): Flow<List<ListeningHistoryEntry>> =
        historyCollection(uid)
            .orderBy("playedAt", Query.Direction.DESCENDING)
            .limit(limit)
            .asStream { snapshot ->
                snapshot.documents.mapNotNull { document ->
                    val songId = document.getString("songId") ?: return@mapNotNull null
                    ListeningHistoryEntry(
                        id = document.id,
                        song = StreamItem(
                            title = document.getString("title").orEmpty().ifBlank { "Unknown title" },
                            url = songId,
                            uploader = document.getString("artist").orEmpty(),
                            thumbnailUrl = document.getString("thumbnailUrl")
                        ),
                        playedAtMs = document.getTimestamp("playedAt")?.toDate()?.time
                    )
                }
            }

    fun getTotalListeningSeconds(uid: String): Flow<Long> =
        callbackFlow {
            val registration = profileRef(uid).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else {
                    trySend(snapshot?.getLong("totalListeningSeconds") ?: 0L)
                }
            }
            awaitClose { registration.remove() }
        }

    suspend fun recordPlayedTrack(uid: String, item: StreamItem) {
        historyCollection(uid).document().set(
            mapOf(
                "songId" to item.url,
                "title" to item.title,
                "artist" to item.uploader,
                "thumbnailUrl" to item.thumbnailUrl,
                "playedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    suspend fun addListeningSeconds(uid: String, seconds: Long) {
        if (seconds <= 0L) return
        profileRef(uid).set(
            mapOf("totalListeningSeconds" to FieldValue.increment(seconds)),
            SetOptions.merge()
        ).await()
    }

    private fun historyCollection(uid: String) =
        firestore.collection("users").document(uid).collection("listeningHistory")

    private fun profileRef(uid: String) =
        firestore.collection("users").document(uid)

    private fun <T> Query.asStream(mapper: (com.google.firebase.firestore.QuerySnapshot) -> T): Flow<T> =
        callbackFlow {
            var registration: ListenerRegistration? = null
            registration = addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else if (snapshot != null) {
                    trySend(mapper(snapshot))
                }
            }
            awaitClose { registration?.remove() }
        }
}