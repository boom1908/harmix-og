package com.boom.harmix.data.local

import android.content.Context
import android.content.SharedPreferences
import com.boom.harmix.auth.UserSession
import com.boom.harmix.auth.UserSessionRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Recent search terms, kept locally so the search bar can suggest them again. */
@Singleton
class SearchHistoryStore @Inject constructor(
    @ApplicationContext context: Context,
    private val userSessionRepository: UserSessionRepository
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("harmix_search_history", Context.MODE_PRIVATE)
    private val firestore = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _history = MutableStateFlow(read())
    val history: StateFlow<List<String>> = _history.asStateFlow()
    private var cloudHistoryListener: ListenerRegistration? = null

    init {
        scope.launch {
            userSessionRepository.session.collectLatest { session ->
                cloudHistoryListener?.remove()
                cloudHistoryListener = null
                if (session is UserSession.Authenticated) {
                    observeCloudHistory(session.uid)
                } else if (session is UserSession.Guest) {
                    _history.value = read()
                } else {
                    _history.value = emptyList()
                }
            }
        }
    }

    fun add(term: String) {
        val clean = term.trim()
        if (clean.isEmpty()) return
        authenticatedUid()?.let { uid ->
            scope.launch {
                historyRef(uid, clean).set(
                    mapOf(
                        "query" to clean,
                        "searchedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            return
        }
        val updated = (listOf(clean) + _history.value.filterNot { it.equals(clean, ignoreCase = true) })
            .take(MAX_ENTRIES)
        persist(updated)
    }

    fun remove(term: String) {
        authenticatedUid()?.let { uid ->
            scope.launch {
                historyRef(uid, term.trim()).delete().await()
            }
            return
        }
        persist(_history.value.filterNot { it.equals(term, ignoreCase = true) })
    }

    fun clear() {
        authenticatedUid()?.let { uid ->
            scope.launch {
                val documents = historyCollection(uid).get().await().documents
                if (documents.isNotEmpty()) {
                    val batch = firestore.batch()
                    documents.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
            }
            return
        }
        persist(emptyList())
    }

    private fun persist(list: List<String>) {
        _history.value = list
        prefs.edit().putString(KEY, list.joinToString(SEPARATOR)).apply()
    }

    private fun read(): List<String> =
        prefs.getString(KEY, "")
            ?.split(SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.take(MAX_ENTRIES)
            .orEmpty()

    private fun observeCloudHistory(uid: String) {
        cloudHistoryListener = historyCollection(uid)
            .orderBy("searchedAt", Query.Direction.DESCENDING)
            .limit(MAX_ENTRIES.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                _history.value = snapshot?.documents
                    ?.mapNotNull { it.getString("query") }
                    .orEmpty()
            }
    }

    private fun authenticatedUid(): String? =
        (userSessionRepository.session.value as? UserSession.Authenticated)?.uid

    private fun historyCollection(uid: String) =
        firestore.collection("users").document(uid).collection("searchHistory")

    private fun historyRef(uid: String, term: String) =
        historyCollection(uid).document(term.lowercase(Locale.ROOT).sha256())

    private fun String.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val KEY = "terms"
        const val SEPARATOR = "\u001F"
        const val MAX_ENTRIES = 12
    }
}
