package com.boom.harmix.auth

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** A signed-in Google identity. Harmix keeps two of these, fully separate. */
data class GoogleAccountInfo(
    val email: String,
    val displayName: String,
    val photoUrl: String?
)

/** Which of the two independent Google slots a sign-in belongs to. */
enum class AccountSlot { MAIN, YT_SYNC }

/** Outcome of asking Google for a YouTube access token. Never silently null. */
sealed class YtTokenResult {
    data class Success(val token: String) : YtTokenResult()
    /** Google needs the user to approve the YouTube scope: launch this intent. */
    data class NeedsConsent(val intent: Intent) : YtTokenResult()
    data object NotConnected : YtTokenResult()
    data class Error(val message: String) : YtTokenResult()
}

private const val TAG = "HarmixGoogleAuth"
private const val TOKEN_TIMEOUT_MS = 15_000L

private const val PREFS = "harmix_google_accounts"
private const val YT_SCOPE = "https://www.googleapis.com/auth/youtube.readonly"

/**
 * Web (server) OAuth client ID from Google Cloud. Required so Google Sign-In
 * issues a real ID token that Firebase / our backend can verify.
 */
const val WEB_CLIENT_ID = "308449061407-kt6ncig4t96s1fvki4384m143i33j050.apps.googleusercontent.com"

/**
 * Handles TWO completely independent Google sign-ins:
 *  - MAIN: the Harmix account (listening history, playlists, liked songs).
 *  - YT_SYNC: a throwaway/second account only used to pull YouTube Music playlists.
 *
 * They never reference each other, so the user can swap the YouTube account
 * as often as they like without touching their Harmix account.
 */
@Singleton
class GoogleAccountsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userSession: UserSessionRepository
) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val firebaseAuth = FirebaseAuth.getInstance()

    private val _mainAccount = MutableStateFlow(readMainAccount())
    val mainAccount: StateFlow<GoogleAccountInfo?> = _mainAccount.asStateFlow()

    private val _ytAccount = MutableStateFlow(read(AccountSlot.YT_SYNC))
    val ytAccount: StateFlow<GoogleAccountInfo?> = _ytAccount.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    init {
        firebaseAuth.addAuthStateListener {
            _mainAccount.value = readMainAccount()
        }
    }

    /** Build a fresh sign-in intent. Always shows the account chooser. */
    fun signInIntent(slot: AccountSlot): Intent {
        val client = clientFor(slot)
        client.signOut() // force the chooser so a different account can be picked
        return client.signInIntent
    }

    /** Feed the Activity result back in here and establish Firebase auth for MAIN. */
    suspend fun handleSignInResult(slot: AccountSlot, data: Intent?): GoogleAccountInfo? {
        val account = runCatching {
            GoogleSignIn.getSignedInAccountFromIntent(data).getResult(
                com.google.android.gms.common.api.ApiException::class.java
            )
        }.getOrNull() ?: return null

        val email = account.email ?: return null
        _authError.value = null
        if (slot == AccountSlot.MAIN) {
            val idToken = account.idToken
            if (idToken.isNullOrBlank()) {
                _authError.value = "Google didn't return an ID token. Check Firebase Google Sign-In setup."
                return null
            }
            try {
                withContext(Dispatchers.IO) {
                    firebaseAuth.signInWithCredential(
                        GoogleAuthProvider.getCredential(idToken, null)
                    ).await()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Firebase sign-in failed", e)
                _authError.value = e.message ?: "Couldn't finish Firebase sign-in. Please try again."
                return null
            }
        }

        val info = GoogleAccountInfo(
            email = email,
            displayName = account.displayName ?: email.substringBefore('@'),
            photoUrl = account.photoUrl?.toString()
        )
        write(slot, info)
        // Drop the global GMS session so the two slots never bleed into each other.
        clientFor(slot).signOut()
        if (slot == AccountSlot.MAIN) userSession.refresh()
        return info
    }

    fun signOut(slot: AccountSlot) {
        runCatching { clientFor(slot).signOut() }
        if (slot == AccountSlot.MAIN) firebaseAuth.signOut()
        write(slot, null)
    }

    /**
     * OAuth access token for the YouTube Data API, for the connected sync account only.
     * Surfaces every failure instead of hiding it, and gives up after 15 seconds so the
     * UI can never spin forever.
     */
    suspend fun youtubeAccessToken(): YtTokenResult = withContext(Dispatchers.IO) {
        val email = _ytAccount.value?.email ?: return@withContext YtTokenResult.NotConnected

        val result = withTimeoutOrNull(TOKEN_TIMEOUT_MS) {
            try {
                val token = GoogleAuthUtil.getToken(
                    context,
                    Account(email, "com.google"),
                    "oauth2:$YT_SCOPE"
                )
                YtTokenResult.Success(token)
            } catch (e: UserRecoverableAuthException) {
                Log.w(TAG, "YouTube token needs user consent", e)
                val intent = e.intent
                if (intent != null) YtTokenResult.NeedsConsent(intent)
                else YtTokenResult.Error(e.message ?: "Google needs extra permission.")
            } catch (e: GoogleAuthException) {
                Log.e(TAG, "Google auth error while getting YouTube token", e)
                YtTokenResult.Error("Google sign-in error: ${e.message ?: e::class.java.simpleName}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get YouTube token", e)
                YtTokenResult.Error("Couldn't get YouTube access: ${e.message ?: e::class.java.simpleName}")
            }
        }

        result ?: YtTokenResult.Error("Google took too long to respond. Please try again.")
    }

    private fun clientFor(slot: AccountSlot): GoogleSignInClient {
        val options = when (slot) {
            AccountSlot.MAIN ->
                GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestProfile()
                    .requestIdToken(WEB_CLIENT_ID)
                    .build()

            AccountSlot.YT_SYNC ->
                GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestProfile()
                    .requestIdToken(WEB_CLIENT_ID)
                    .requestScopes(Scope(YT_SCOPE))
                    .build()
        }
        return GoogleSignIn.getClient(context, options)
    }

    private fun key(slot: AccountSlot) = if (slot == AccountSlot.MAIN) "main" else "yt"

    private fun read(slot: AccountSlot): GoogleAccountInfo? {
        val k = key(slot)
        val email = prefs.getString("${k}_email", null) ?: return null
        return GoogleAccountInfo(
            email = email,
            displayName = prefs.getString("${k}_name", email.substringBefore('@')).orEmpty(),
            photoUrl = prefs.getString("${k}_photo", null)
        )
    }

    private fun readMainAccount(): GoogleAccountInfo? {
        val firebaseUser = firebaseAuth.currentUser ?: return null
        val saved = read(AccountSlot.MAIN)
        return if (saved?.email == firebaseUser.email) {
            saved
        } else {
            GoogleAccountInfo(
                email = firebaseUser.email.orEmpty(),
                displayName = firebaseUser.displayName.orEmpty()
                    .ifBlank { firebaseUser.email?.substringBefore('@').orEmpty() },
                photoUrl = firebaseUser.photoUrl?.toString()
            )
        }
    }

    private fun write(slot: AccountSlot, info: GoogleAccountInfo?) {
        val k = key(slot)
        prefs.edit().apply {
            if (info == null) {
                remove("${k}_email"); remove("${k}_name"); remove("${k}_photo")
            } else {
                putString("${k}_email", info.email)
                putString("${k}_name", info.displayName)
                putString("${k}_photo", info.photoUrl)
            }
        }.apply()

        when (slot) {
            AccountSlot.MAIN -> _mainAccount.value = info
            AccountSlot.YT_SYNC -> _ytAccount.value = info
        }
    }
}