package com.xymonitor.app

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("xymonitor", Context.MODE_PRIVATE)

    var userId: String
        get() = sp.getString(KEY_USER_ID, "").orEmpty()
        set(value) = sp.edit().putString(KEY_USER_ID, value.trim()).apply()

    var running: Boolean
        get() = sp.getBoolean(KEY_RUNNING, false)
        set(value) = sp.edit().putBoolean(KEY_RUNNING, value).apply()

    var lastCheckAt: Long
        get() = sp.getLong(KEY_LAST_CHECK, 0L)
        set(value) = sp.edit().putLong(KEY_LAST_CHECK, value).apply()

    var lastStatus: String
        get() = sp.getString(KEY_LAST_STATUS, "未开始").orEmpty()
        set(value) = sp.edit().putString(KEY_LAST_STATUS, value).apply()

    var lastError: String
        get() = sp.getString(KEY_LAST_ERROR, "").orEmpty()
        set(value) = sp.edit().putString(KEY_LAST_ERROR, value).apply()

    val knownIds: Set<String>
        get() = sp.getStringSet(KEY_KNOWN_IDS, emptySet())?.toSet().orEmpty()

    fun resetKnownIfUserChanged(userId: String) {
        val previous = sp.getString(KEY_KNOWN_USER, "")
        if (previous != userId) {
            sp.edit()
                .putString(KEY_KNOWN_USER, userId)
                .putStringSet(KEY_KNOWN_IDS, emptySet())
                .apply()
        }
    }

    fun replaceKnown(ids: Collection<String>) {
        sp.edit().putStringSet(KEY_KNOWN_IDS, ids.toSet()).apply()
    }

    fun addKnown(ids: Collection<String>) {
        val next = knownIds.toMutableSet()
        next.addAll(ids.filter { it.isNotBlank() })
        sp.edit().putStringSet(KEY_KNOWN_IDS, next).apply()
    }

    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_RUNNING = "running"
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_LAST_STATUS = "last_status"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_KNOWN_IDS = "known_ids"
        private const val KEY_KNOWN_USER = "known_user"
    }
}
