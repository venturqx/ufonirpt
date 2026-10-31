package org.schabi.newpipe.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class TournesolAuthStorage(
    private val legacyPrefs: SharedPreferences,
    private val openEncryptedPrefs: () -> SharedPreferences,
    private val wipeStorage: () -> Unit
) {
    @Volatile
    private var openedPrefs: SharedPreferences? = null

    fun prefs(resetIfUnreadable: Boolean = false): SharedPreferences? {
        openedPrefs?.let { return it }
        return synchronized(this) {
            openedPrefs ?: open(resetIfUnreadable)?.also { openedPrefs = it }
        }
    }

    private fun open(resetIfUnreadable: Boolean): SharedPreferences? {
        val encryptedPrefs = try {
            openEncryptedPrefs()
        } catch (firstFailure: Exception) {
            if (!resetIfUnreadable) {
                Log.w(TAG, "Encrypted auth prefs are unavailable.", firstFailure)
                return null
            }
            Log.w(TAG, "Encrypted auth prefs are unreadable. Resetting auth storage.", firstFailure)
            wipeStorage()
            try {
                openEncryptedPrefs()
            } catch (secondFailure: Exception) {
                Log.e(TAG, "Failed to recreate encrypted auth prefs after reset", secondFailure)
                null
            }
        } ?: return null

        migrateLegacyAuthState(encryptedPrefs)

        return encryptedPrefs
    }

    private fun migrateLegacyAuthState(encryptedPrefs: SharedPreferences) {
        if (!legacyPrefs.contains(AUTH_STATE_KEY)) {
            return
        }

        val legacyValue = legacyPrefs.getString(AUTH_STATE_KEY, null)
        val needsCopy = !legacyValue.isNullOrBlank() && !encryptedPrefs.contains(AUTH_STATE_KEY)
        if (needsCopy && !encryptedPrefs.edit().putString(AUTH_STATE_KEY, legacyValue).commit()) {
            return
        }
        legacyPrefs.edit().remove(AUTH_STATE_KEY).apply()
    }

    companion object {
        const val AUTH_STATE_KEY = "tournesol_auth_state"
        private const val TAG = "TournesolAuthStorage"
        private const val AUTH_STATE_PREFS_NAME = "tournesol_auth_state_prefs"

        fun create(appContext: Context) = TournesolAuthStorage(
            legacyPrefs = PreferenceManager.getDefaultSharedPreferences(appContext),
            openEncryptedPrefs = { createEncryptedAuthPrefs(appContext) },
            wipeStorage = { clearAuthStateStorage(appContext) }
        )

        private fun createEncryptedAuthPrefs(appContext: Context): SharedPreferences {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            return EncryptedSharedPreferences.create(
                AUTH_STATE_PREFS_NAME,
                masterKeyAlias,
                appContext,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

        private fun clearAuthStateStorage(appContext: Context) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    appContext.deleteSharedPreferences(AUTH_STATE_PREFS_NAME)
                } else {
                    appContext.getSharedPreferences(AUTH_STATE_PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .clear()
                        .commit()
                }
            }
        }
    }
}
