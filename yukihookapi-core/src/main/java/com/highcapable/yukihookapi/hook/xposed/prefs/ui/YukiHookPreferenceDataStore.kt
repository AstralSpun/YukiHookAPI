/*
 * YukiHookAPI - An efficient Hook API and Xposed Module solution built in Kotlin.
 * Copyright (C) 2019 HighCapable
 * https://github.com/HighCapable/YukiHookAPI
 *
 * Apache License Version 2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.highcapable.yukihookapi.hook.xposed.prefs.ui

import android.content.Context
import androidx.preference.PreferenceDataStore
import com.highcapable.yukihookapi.hook.xposed.prefs.YukiHookPrefsBridge

/** Routes AndroidX Preference reads and writes through [YukiHookPrefsBridge]. */
internal class YukiHookPreferenceDataStore(context: Context, prefsName: String) : PreferenceDataStore() {

    /** Preferences bridge for the current preference hierarchy. */
    private val preferences = YukiHookPrefsBridge.from(context).name(prefsName)

    override fun putString(key: String, value: String?) {
        preferences.edit { if (value == null) remove(key) else putString(key, value) }
    }

    override fun putStringSet(key: String, values: Set<String>?) {
        preferences.edit { if (values == null) remove(key) else putStringSet(key, values) }
    }

    override fun putInt(key: String, value: Int) = preferences.edit { putInt(key, value) }

    override fun putLong(key: String, value: Long) = preferences.edit { putLong(key, value) }

    override fun putFloat(key: String, value: Float) = preferences.edit { putFloat(key, value) }

    override fun putBoolean(key: String, value: Boolean) = preferences.edit { putBoolean(key, value) }

    override fun getString(key: String, defValue: String?) =
        preferences.getString(key, defValue ?: "").takeIf { preferences.contains(key) } ?: defValue

    override fun getStringSet(key: String, defValues: Set<String>?) =
        preferences.getStringSet(key, defValues ?: emptySet()).takeIf { preferences.contains(key) } ?: defValues

    override fun getInt(key: String, defValue: Int) = preferences.getInt(key, defValue)

    override fun getLong(key: String, defValue: Long) = preferences.getLong(key, defValue)

    override fun getFloat(key: String, defValue: Float) = preferences.getFloat(key, defValue)

    override fun getBoolean(key: String, defValue: Boolean) = preferences.getBoolean(key, defValue)
}