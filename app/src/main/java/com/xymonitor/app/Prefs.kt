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

    var intervalA: Int
        get() = Interval.clampSeconds(sp.getInt(KEY_INTERVAL_A, Interval.DEFAULT_A))
        set(value) = sp.edit().putInt(KEY_INTERVAL_A, Interval.clampSeconds(value)).apply()

    var intervalB: Int
        get() = Interval.clampSeconds(sp.getInt(KEY_INTERVAL_B, Interval.DEFAULT_B))
        set(value) = sp.edit().putInt(KEY_INTERVAL_B, Interval.clampSeconds(value)).apply()

    var errorSound: Boolean
        get() = sp.getBoolean(KEY_ERROR_SOUND, true)
        set(value) = sp.edit().putBoolean(KEY_ERROR_SOUND, value).apply()

    var newItemSoundUri: String
        get() = sp.getString(KEY_NEW_ITEM_SOUND, "").orEmpty()
        set(value) = sp.edit().putString(KEY_NEW_ITEM_SOUND, value).apply()

    var nextWaitMs: Long
        get() = sp.getLong(KEY_NEXT_WAIT, 0L)
        set(value) = sp.edit().putLong(KEY_NEXT_WAIT, value).apply()

    var lastFirstItemId: String
        get() = sp.getString(KEY_LAST_FIRST_ID, "").orEmpty()
        set(value) = sp.edit().putString(KEY_LAST_FIRST_ID, value).apply()

    var lastPlannedWaitMs: Long
        get() = sp.getLong(KEY_PLANNED_WAIT, 0L)
        set(value) = sp.edit().putLong(KEY_PLANNED_WAIT, value).apply()

    var lastActualGapMs: Long
        get() = sp.getLong(KEY_ACTUAL_GAP, 0L)
        set(value) = sp.edit().putLong(KEY_ACTUAL_GAP, value).apply()

    var nextInspectAt: Long
        get() = sp.getLong(KEY_NEXT_AT, 0L)
        set(value) = sp.edit().putLong(KEY_NEXT_AT, value).apply()

    fun resetFirstIdIfUserChanged(userId: String) {
        val previous = sp.getString(KEY_KNOWN_USER, "")
        if (previous != userId) {
            sp.edit()
                .putString(KEY_KNOWN_USER, userId)
                .putString(KEY_LAST_FIRST_ID, "")
                .apply()
        }
    }

    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_RUNNING = "running"
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_LAST_STATUS = "last_status"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_LAST_FIRST_ID = "last_first_id"
        private const val KEY_KNOWN_USER = "known_user"
        private const val KEY_INTERVAL_A = "interval_a"
        private const val KEY_INTERVAL_B = "interval_b"
        private const val KEY_ERROR_SOUND = "error_sound"
        private const val KEY_NEW_ITEM_SOUND = "new_item_sound"
        private const val KEY_NEXT_WAIT = "next_wait"
        private const val KEY_PLANNED_WAIT = "planned_wait"
        private const val KEY_ACTUAL_GAP = "actual_gap"
        private const val KEY_NEXT_AT = "next_inspect_at"
    }
}
