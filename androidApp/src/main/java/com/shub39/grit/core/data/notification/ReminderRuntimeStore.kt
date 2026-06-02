/*
 * Copyright (C) 2026  Shubham Gorai
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.shub39.grit.core.data.notification

import android.content.Context
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

@Single
class ReminderRuntimeStore(private val context: Context) {
    companion object {
        private const val PREF_NAME = "grit_reminder_runtime"
        private const val TRACKED_SESSION_KEY = "tracked_session"
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    internal fun getTrackedSession(): ReminderSession? =
        prefs.getString(TRACKED_SESSION_KEY, null)?.let { sessionJson ->
            runCatching { Json.decodeFromString<ReminderSession>(sessionJson) }.getOrNull()
        }

    internal fun setTrackedSession(session: ReminderSession) {
        prefs.edit().putString(TRACKED_SESSION_KEY, Json.encodeToString(session)).apply()
    }

    internal fun clearTrackedSession() {
        prefs.edit().remove(TRACKED_SESSION_KEY).apply()
    }
}
