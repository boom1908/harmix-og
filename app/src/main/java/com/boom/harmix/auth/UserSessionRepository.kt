package com.boom.harmix.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class UserSession {
    data object Loading : UserSession()
    data object Guest : UserSession()

    data class NeedsUsername(
        val uid: String,
        val email: String,
        val displayName: String
    ) : UserSession()

    data class Authenticated(
        val uid: String,
        val username: String,
        val email: String,
        val displayName: String
    ) : UserSession()

    data class Error(val message: String) : UserSession()
}

sealed class UsernameSaveResult {
    data class Success(val username: String) : UsernameSaveResult()
    data object UsernameTaken : UsernameSaveResult()
    data class Failure(val message: String) : UsernameSaveResult()
}

/**
 * Owns the Firebase user session and the small profile needed before the
 * cloud-backed account features are enabled in later phases.
 */
@Singleton
class UserSessionRepository @Inject constructor() {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _session = MutableStateFlow<UserSession>(UserSession.Loading)
    val session: StateFlow<UserSession> = _session.asStateFlow()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            scope.launch { resolve(firebaseAuth.currentUser) }
        }
    }

    fun refresh() {
        scope.launch { resolve(auth.currentUser) }
    }

    suspend fun saveUsername(chosenName: String): UsernameSaveResult {
        val user = auth.currentUser
            ?: return UsernameSaveResult.Failure("Sign in before choosing a username.")
        val username = chosenName

        return withContext(Dispatchers.IO) {
            try {
                val profileRef = firestore.collection("users").document(user.uid)
                val usernameRef = firestore.collection("usernames").document(username)
                val task = firestore.runTransaction { transaction ->
                    val profileSnapshot = transaction.get(profileRef)
                    val newUsernameSnapshot = transaction.get(usernameRef)
                    val oldUsername = profileSnapshot.getString("username")
                    val oldUsernameSnapshot = if (!oldUsername.isNullOrBlank() && oldUsername != username) {
                        transaction.get(firestore.collection("usernames").document(oldUsername))
                    } else {
                        null
                    }

                    if (newUsernameSnapshot.exists() &&
                        newUsernameSnapshot.getString("uid") != user.uid
                    ) {
                        throw UsernameTakenException()
                    }

                    if (!newUsernameSnapshot.exists()) {
                        transaction.set(usernameRef, mapOf("uid" to user.uid))
                    }
                    if (oldUsernameSnapshot?.exists() == true) {
                        transaction.delete(oldUsernameSnapshot.reference)
                    }

                    val profileData = hashMapOf<String, Any>(
                        "username" to username,
                        "email" to (user.email ?: ""),
                        "totalListeningSeconds" to (
                            profileSnapshot.getLong("totalListeningSeconds") ?: 0L
                            )
                    )
                    if (!profileSnapshot.exists()) {
                        profileData["createdAt"] = FieldValue.serverTimestamp()
                    } else if (!profileSnapshot.contains("createdAt")) {
                        profileData["createdAt"] = FieldValue.serverTimestamp()
                    }
                    transaction.set(profileRef, profileData, SetOptions.merge())
                    true
                }
                task.await()
                resolve(user)
                UsernameSaveResult.Success(username)
            } catch (e: Exception) {
                if (e.findCause<UsernameTakenException>() != null) {
                    UsernameSaveResult.UsernameTaken
                } else {
                    UsernameSaveResult.Failure(
                        e.message ?: "We couldn't save that username. Please try again."
                    )
                }
            }
        }
    }

    private suspend fun resolve(user: FirebaseUser?) {
        if (user == null) {
            _session.value = UserSession.Guest
            return
        }
        if (auth.currentUser?.uid != user.uid) return

        try {
            val profile = firestore.collection("users").document(user.uid).get().await()
            if (auth.currentUser?.uid != user.uid) return
            val username = profile.getString("username").orEmpty()
            if (username.isBlank()) {
                _session.value = UserSession.NeedsUsername(
                    uid = user.uid,
                    email = user.email.orEmpty(),
                    displayName = user.displayName.orEmpty().ifBlank { user.email.orEmpty() }
                )
            } else {
                _session.value = UserSession.Authenticated(
                    uid = user.uid,
                    username = username,
                    email = user.email.orEmpty(),
                    displayName = user.displayName.orEmpty().ifBlank { username }
                )
            }
        } catch (e: Exception) {
            _session.value = UserSession.Error(
                e.message ?: "Couldn't load your Harmix profile. Check your connection and retry."
            )
        }
    }

    private class UsernameTakenException : Exception()

    private inline fun <reified T : Throwable> Throwable.findCause(): T? {
        var current: Throwable? = this
        while (current != null) {
            if (current is T) return current
            current = current.cause
        }
        return null
    }
}