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
 *
 * This file is created by fankes on 2022/2/8.
 */
@file:Suppress(
    "unused", "MemberVisibilityCanBePrivate", "StaticFieldLeak", "SetWorldReadable",
    "CommitPrefEdits", "UNCHECKED_CAST", "NON_PUBLIC_CALL_FROM_PUBLIC_INLINE"
)

package com.highcapable.yukihookapi.hook.xposed.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceFragmentCompat
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.xposed.bridge.YukiXposedModule
import com.highcapable.yukihookapi.hook.xposed.bridge.delegate.XSharedPreferencesDelegate
import com.highcapable.yukihookapi.hook.xposed.bridge.service.YukiXposedService
import com.highcapable.yukihookapi.hook.xposed.parasitic.AppParasitics
import com.highcapable.yukihookapi.hook.xposed.prefs.data.PrefsData
import com.highcapable.yukihookapi.hook.xposed.prefs.ui.ModulePreferenceFragment
import java.io.File
import java.util.Collections
import java.util.WeakHashMap

/**
 * [YukiHookAPI] extended storage bridge implementation for local and libxposed remote [SharedPreferences].
 *
 * Selects the storage object intelligently for different environments.
 *
 * - Hook processes read framework-owned remote groups.
 *
 * - Module apps prefer the primary libxposed remote group and mirror writes to every connected framework service and local storage.
 *
 * For using [PreferenceFragmentCompat] in the module environment, [YukiHookAPI] provides [ModulePreferenceFragment] with the same functionality.
 * @param context the context instance, null by default.
 */
class YukiHookPrefsBridge private constructor(private var context: Context? = null) {

    internal companion object {

        /** Private storage containing completed remote preference migrations. */
        private const val REMOTE_PREFS_MIGRATION_STATE = "com.highcapable.yukihookapi.remote_preferences"

        /** Whether the current environment is a (Xposed) host environment. */
        private val isXposedEnvironment = YukiXposedModule.isXposedEnvironment

        /** Currently cached [XSharedPreferencesDelegate] instances. */
        private val xPrefs = mutableMapOf<String, XSharedPreferencesDelegate>()

        /** Currently cached [SharedPreferences] instances. */
        private val sPrefs = mutableMapOf<String, SharedPreferences>()

        /** Serializes local and remote preference snapshot synchronization. */
        private val remotePrefsLock = Any()

        /** Remote preference proxies whose synchronous snapshot update failed. */
        private val unavailableRemotePrefs = Collections.newSetFromMap(WeakHashMap<SharedPreferences, Boolean>())

        /**
         * Creates a [YukiHookPrefsBridge] object.
         * @param context the context instance, null in the (Xposed) host environment.
         * @return [YukiHookPrefsBridge]
         */
        internal fun from(context: Context? = null) = YukiHookPrefsBridge(context)

        /**
         * Makes the preferences file globally readable and writable.
         * @param context the context instance.
         * @param prefsFileName the SharedPreferences file name.
         */
        internal fun makeWorldReadable(context: Context?, prefsFileName: String) {
            runCatching {
                context?.also {
                    File(File(it.applicationInfo.dataDir, "shared_prefs"), prefsFileName).apply {
                        setReadable(true, false)
                        setExecutable(true, false)
                    }
                }
            }
        }
    }

    /** Storage name. */
    private var prefsName = ""

    /** Whether to use the new storage approach for EdXposed and LSPosed. */
    private var isUsingNewXSharedPreferences = false

    /** Whether native storage is enabled. */
    private var isUsingNativeStorage = false

    /**
     * Gets the current storage name, package name plus _preferences by default.
     * @return [String]
     */
    private val currentPrefsName
        get() = prefsName.ifBlank {
            if (isUsingNativeStorage) "${context?.packageName ?: "unknown"}_preferences"
            else "${YukiXposedModule.modulePackageName.ifBlank { context?.packageName ?: "unknown" }}_preferences"
        }

    /** Checks the API loading state. */
    private fun checkApi() {
        if (YukiHookAPI.isLoadedFromBaseContext) error("YukiHookPrefsBridge not allowed in Custom Hook API")
        if (isXposedEnvironment && YukiXposedModule.modulePackageName.isBlank())
            error("Xposed modulePackageName load failed, please reset and rebuild it")
    }

    /**
     * Makes the preferences file globally readable and writable.
     * @param callback the callback block.
     * @return [T]
     */
    private inline fun <T> makeWorldReadable(callback: () -> T): T {
        val result = callback()
        if (isXposedEnvironment.not() && isUsingNewXSharedPreferences.not())
            runCatching { makeWorldReadable(context, prefsFileName = "$currentPrefsName.xml") }
        return result
    }

    /**
     * Gets the current remote [SharedPreferences] object.
     * @return [SharedPreferences]
     */
    private val currentXsp
        get() = checkApi().let {
            runCatching {
                (xPrefs[currentPrefsName]?.instance ?: XSharedPreferencesDelegate.from(YukiXposedModule.modulePackageName, currentPrefsName)
                    .also {
                        xPrefs[currentPrefsName] = it
                    }.instance)
            }.onFailure { YLog.innerE(it.message ?: "Operating system not supported", it) }.getOrNull()
                ?: error("Cannot load remote preferences, maybe your Hook Framework does not support them")
        }

    /**
     * Gets the current [SharedPreferences] object.
     * @return [SharedPreferences]
     */
    private val currentSp
        get() = checkApi().let {
            runCatching {
                @Suppress("DEPRECATION", "WorldReadableFiles")
                sPrefs[context.toString() + currentPrefsName] ?: context?.getSharedPreferences(currentPrefsName, Context.MODE_WORLD_READABLE)
                    ?.also {
                        isUsingNewXSharedPreferences = true
                        sPrefs[context.toString() + currentPrefsName] = it
                    } ?: error("YukiHookPrefsBridge missing Context instance")
            }.getOrElse {
                sPrefs[context.toString() + currentPrefsName] ?: context?.getSharedPreferences(currentPrefsName, Context.MODE_PRIVATE)?.also {
                    isUsingNewXSharedPreferences = false
                    sPrefs[context.toString() + currentPrefsName] = it
                } ?: error("YukiHookPrefsBridge missing Context instance")
            }
        }

    /**
     * Gets the current readable [SharedPreferences] object.
     * @return [SharedPreferences]
     */
    private val currentReadableSp
        get() = when {
            isXposedEnvironment && isUsingNativeStorage.not() -> currentXsp
            isUsingNativeStorage -> currentSp
            else -> currentRemotePreferences().firstOrNull() ?: currentSp
        }

    /**
     * Gets responsive remote preferences and synchronizes their snapshots with local storage.
     *
     * On first connection, values are merged without dropping unique keys and earlier-bound framework services win conflicts. After migration,
     * the local mirror becomes the canonical snapshot and is pushed completely to newly connected services, including deletions.
     * @return [List] of synchronized remote [SharedPreferences].
     */
    private fun currentRemotePreferences(): List<SharedPreferences> {
        val remotePreferences = YukiXposedService.remotePreferences(currentPrefsName)
        if (remotePreferences.isEmpty()) return emptyList()
        return synchronized(remotePrefsLock) {
            val availableRemotePreferences = remotePreferences.filterNot { it in unavailableRemotePrefs }
            if (availableRemotePreferences.isEmpty()) return@synchronized emptyList()
            val localPreferences = currentSp
            val localValues = localPreferences.all
            val remoteValues = availableRemotePreferences.mapNotNull { preferences ->
                runCatching { preferences to preferences.all }
                    .onFailure { YLog.innerE("Failed to read libxposed remote preferences group $currentPrefsName", it) }.getOrNull()
            }
            if (remoteValues.isEmpty()) return@synchronized emptyList()
            val isMigrationCompleted = isRemotePrefsMigrationCompleted()
            val canonicalValues = if (isMigrationCompleted) localValues else mutableMapOf<String, Any?>().apply {
                putAll(localValues)
                remoteValues.asReversed().forEach { (_, values) -> putAll(values) }
            }
            val synchronizedRemotePreferences = remoteValues.mapNotNull { (preferences, values) ->
                if (values == canonicalValues || preferences.replaceAll(canonicalValues)) preferences
                else {
                    unavailableRemotePrefs += preferences
                    null
                }
            }
            val isLocalSynchronized = localValues == canonicalValues || localPreferences.replaceAll(canonicalValues)
            if (isMigrationCompleted.not() && synchronizedRemotePreferences.isNotEmpty() && isLocalSynchronized)
                completeRemotePrefsMigration()
            synchronizedRemotePreferences
        }
    }

    /** Whether the one-time local and remote snapshot migration has completed for the current group. */
    private fun isRemotePrefsMigrationCompleted() = migrationStatePreferences?.getBoolean(currentPrefsName, false) == true

    /** Marks the one-time local and remote snapshot migration as completed for the current group. */
    private fun completeRemotePrefsMigration() {
        runCatching { migrationStatePreferences?.edit()?.putBoolean(currentPrefsName, true)?.commit() }
            .onFailure { YLog.innerE("Failed to persist remote preferences migration state for $currentPrefsName", it) }
    }

    /** Private migration state storage for the current module application. */
    private val migrationStatePreferences
        get() = context?.applicationContext?.getSharedPreferences(REMOTE_PREFS_MIGRATION_STATE, Context.MODE_PRIVATE)

    /** Replaces the complete preference snapshot synchronously. */
    private fun SharedPreferences.replaceAll(values: Map<String, Any?>) = runCatching {
        val editor = edit().clear()
        values.forEach { (key, value) ->
            when (value) {
                null -> editor.remove(key)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value as Set<String>)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Float -> editor.putFloat(key, value)
                is Long -> editor.putLong(key, value)
                else -> error("Key-Value type ${value.javaClass.name} is not allowed")
            }
        }
        editor.commit()
    }.onFailure { YLog.innerE("Failed to synchronize preferences group $currentPrefsName", it) }.getOrNull() ?: false

    /**
     * Whether remote preferences are readable.
     *
     * - This API is deprecated and will be removed in a future version.
     *
     * - Migrate to [isPreferencesAvailable].
     * @return [Boolean]
     */
    @Deprecated(message = "Use the new approach to implement this feature", ReplaceWith("isPreferencesAvailable"))
    val isXSharePrefsReadable get() = isPreferencesAvailable

    /**
     * Whether [YukiHookPrefsBridge] is running with the highest EdXposed or LSPosed privileges.
     *
     * - This API is deprecated and will be removed in a future version.
     *
     * - Migrate to [isPreferencesAvailable].
     * @return [Boolean]
     */
    @Deprecated(message = "Use the new approach to implement this feature", ReplaceWith("isPreferencesAvailable"))
    val isRunInNewXShareMode get() = isPreferencesAvailable

    /**
     * Gets the availability state of the current [YukiHookPrefsBridge].
     *
     * - In a (Xposed) host environment, returns whether libxposed remote preferences are available.
     *
     * - In the module environment, checks the primary libxposed remote group first and falls back to local storage.
     * @return [Boolean]
     */
    val isPreferencesAvailable
        get() = when {
            isXposedEnvironment && isUsingNativeStorage.not() -> runCatching { currentXsp.all; true }.getOrNull() ?: false
            isUsingNativeStorage -> runCatching { currentSp.all; true }.getOrNull() ?: false
            else -> runCatching {
                if (currentRemotePreferences().isNotEmpty()) true
                else {
                    currentSp.edit()
                    isUsingNewXSharedPreferences
                }
            }.getOrNull() ?: false
        }

    /**
     * Customizes the SharedPreferences storage name.
     * @param name the custom SharedPreferences storage name.
     * @return [YukiHookPrefsBridge]
     */
    fun name(name: String): YukiHookPrefsBridge {
        prefsName = name
        return this
    }

    /**
     * Reads key-value data directly without using the cache.
     *
     * - This function and feature have been removed. They will be deleted in a future version.
     *
     * - Direct key-value caching has been removed because it can cause out-of-memory (OOM) issues.
     * @return [YukiHookPrefsBridge]
     */
    @Deprecated(message = "This function and feature have been removed. Delete this function", ReplaceWith("this"))
    fun direct() = this

    /**
     * Ignores the current environment and uses [Context.getSharedPreferences] directly to access data.
     * @return [YukiHookPrefsBridge]
     * @throws IllegalStateException if [context] is null.
     */
    fun native(): YukiHookPrefsBridge {
        if (isXposedEnvironment && context == null) context = AppParasitics.currentApplication
            ?: error("The Host App's Context has not yet initialized successfully, the native function cannot be used at this time")
        isUsingNativeStorage = true
        return this
    }

    /**
     * Gets a [String] value.
     *
     * - Detects the corresponding environment intelligently when reading key-value data.
     *
     * - Using [PrefsData] to create a template and [get] to retrieve data is recommended.
     * @param key the key name.
     * @param value the default value, "" by default.
     * @return [String]
     */
    fun getString(key: String, value: String = "") = makeWorldReadable {
        currentReadableSp.getString(key, value) ?: value
    }

    /**
     * Gets a [Set] of [String] values.
     *
     * - Detects the corresponding environment intelligently when reading key-value data.
     *
     * - Using [PrefsData] to create a template and [get] to retrieve data is recommended.
     * @param key the key name.
     * @param value the default value, an empty [MutableSet] of [String] values by default.
     * @return [Set]<[String]>
     */
    fun getStringSet(key: String, value: Set<String> = mutableSetOf()) = makeWorldReadable {
        currentReadableSp.getStringSet(key, value) ?: value
    }

    /**
     * Gets a [Boolean] value.
     *
     * - Detects the corresponding environment intelligently when reading key-value data.
     *
     * - Using [PrefsData] to create a template and [get] to retrieve data is recommended.
     * @param key the key name.
     * @param value the default value, false by default.
     * @return [Boolean]
     */
    fun getBoolean(key: String, value: Boolean = false) = makeWorldReadable {
        currentReadableSp.getBoolean(key, value)
    }

    /**
     * Gets an [Int] value.
     *
     * - Detects the corresponding environment intelligently when reading key-value data.
     *
     * - Using [PrefsData] to create a template and [get] to retrieve data is recommended.
     * @param key the key name.
     * @param value the default value, 0 by default.
     * @return [Int]
     */
    fun getInt(key: String, value: Int = 0) = makeWorldReadable {
        currentReadableSp.getInt(key, value)
    }

    /**
     * Gets a [Float] value.
     *
     * - Detects the corresponding environment intelligently when reading key-value data.
     *
     * - Using [PrefsData] to create a template and [get] to retrieve data is recommended.
     * @param key the key name.
     * @param value the default value, 0f by default.
     * @return [Float]
     */
    fun getFloat(key: String, value: Float = 0f) = makeWorldReadable {
        currentReadableSp.getFloat(key, value)
    }

    /**
     * Gets a [Long] value.
     *
     * - Detects the corresponding environment intelligently when reading key-value data.
     *
     * - Using [PrefsData] to create a template and [get] to retrieve data is recommended.
     * @param key the key name.
     * @param value the default value, 0L by default.
     * @return [Long]
     */
    fun getLong(key: String, value: Long = 0L) = makeWorldReadable {
        currentReadableSp.getLong(key, value)
    }

    /**
     * Gets a value of the specified type intelligently.
     * @param prefs the key-value instance.
     * @param value the default value. The default is [PrefsData.value] in [prefs].
     * @return [T] which can only be [String], [Set] of [String], [Int], [Float], [Long], or [Boolean].
     */
    inline fun <reified T> get(prefs: PrefsData<T>, value: T = prefs.value): T = getPrefsData(prefs.key, value) as T

    /**
     * Gets a value of the specified type intelligently.
     *
     * Wrapper function for calling the inline function.
     * @param key the key.
     * @param value the default value.
     * @return [Any]
     */
    private fun getPrefsData(key: String, value: Any?): Any = when (value) {
        is String -> getString(key, value)
        is Set<*> -> getStringSet(key, value as? Set<String> ?: error("Key-Value type ${value.javaClass.name} is not allowed"))
        is Int -> getInt(key, value)
        is Float -> getFloat(key, value)
        is Long -> getLong(key, value)
        is Boolean -> getBoolean(key, value)
        else -> error("Key-Value type ${value?.javaClass?.name} is not allowed")
    }

    /**
     * Whether data for [key] exists.
     *
     * - Detects the corresponding environment intelligently when reading key-value data.
     * @return [Boolean] whether the key exists.
     */
    fun contains(key: String) = currentReadableSp.contains(key)

    /**
     * Gets all stored key-value data.
     *
     * - Detects the corresponding environment intelligently when reading key-value data.
     *
     * - Each call retrieves real-time data without cache control. Do not use this in highly concurrent scenarios.
     * @return [MutableMap] containing key-value data of all types.
     */
    fun all() = mutableMapOf<String, Any?>().apply {
        currentReadableSp.all.forEach { (k, v) -> this[k] = v }
    }

    /**
     * Removes all stored data containing [key].
     *
     * - This API is deprecated and will be removed in a future version.
     *
     * - Migrate to [edit].
     * @param key the key name.
     */
    @Deprecated(message = "This function is deprecated due to performance issues. Migrate to the new usage", ReplaceWith("edit { remove(key) }"))
    fun remove(key: String) = edit { remove(key) }

    /**
     * Removes the stored data for [PrefsData.key].
     *
     * - This API is deprecated and will be removed in a future version.
     *
     * - Migrate to [edit].
     * @param prefs the key-value instance.
     */
    @Deprecated(message = "This function is deprecated due to performance issues. Migrate to the new usage", ReplaceWith("edit { remove(prefs) }"))
    inline fun <reified T> remove(prefs: PrefsData<T>) = edit { remove(prefs) }

    /**
     * Removes all stored data.
     *
     * - This API is deprecated and will be removed in a future version.
     *
     * - Migrate to [edit].
     */
    @Deprecated(message = "This function is deprecated due to performance issues. Migrate to the new usage", ReplaceWith("edit { clear() }"))
    fun clear() = edit { clear() }

    /**
     * Stores a [String] value.
     *
     * - This API is deprecated and will be removed in a future version.
     *
     * - Migrate to [edit].
     * @param key the key name.
     * @param value the value data.
     */
    @Deprecated(message = "This function is deprecated due to performance issues. Migrate to the new usage", ReplaceWith("edit { putString(key, value) }"))
    fun putString(key: String, value: String) = edit { putString(key, value) }

    /**
     * Stores a [Set] of [String] values.
     *
     * - This API is deprecated and will be removed in a future version.
     *
     * - Migrate to [edit].
     * @param key the key name.
     * @param value the value data.
     */
    @Deprecated(message = "This function is deprecated due to performance issues. Migrate to the new usage", ReplaceWith("edit { putStringSet(key, value) }"))
    fun putStringSet(key: String, value: Set<String>) = edit { putStringSet(key, value) }

    /**
     * Stores a [Boolean] value.
     *
     * - This API is deprecated and will be removed in a future version.
     *
     * - Migrate to [edit].
     * @param key the key name.
     * @param value the value data.
     */
    @Deprecated(message = "This function is deprecated due to performance issues. Migrate to the new usage", ReplaceWith("edit { putBoolean(key, value) }"))
    fun putBoolean(key: String, value: Boolean) = edit { putBoolean(key, value) }

    /**
     * Stores an [Int] value.
     *
     * - This API is deprecated and will be removed in a future version.
     *
     * - Migrate to [edit].
     * @param key the key name.
     * @param value the value data.
     */
    @Deprecated(message = "This function is deprecated due to performance issues. Migrate to the new usage", ReplaceWith("edit { putInt(key, value) }"))
    fun putInt(key: String, value: Int) = edit { putInt(key, value) }

    /**
     * Stores a [Float] value.
     *
     * - This API is deprecated and will be removed in a future version.
     *
     * - Migrate to [edit].
     * @param key the key name.
     * @param value the value data.
     */
    @Deprecated(message = "This function is deprecated due to performance issues. Migrate to the new usage", ReplaceWith("edit { putFloat(key, value) }"))
    fun putFloat(key: String, value: Float) = edit { putFloat(key, value) }

    /**
     * Stores a [Long] value.
     *
     * - This API is deprecated and will be removed in a future version.
     *
     * - Migrate to [edit].
     * @param key the key name.
     * @param value the value data.
     */
    @Deprecated(message = "This function is deprecated due to performance issues. Migrate to the new usage", ReplaceWith("edit { putLong(key, value) }"))
    fun putLong(key: String, value: Long) = edit { putLong(key, value) }

    /**
     * Stores a value of the specified type intelligently.
     *
     * - This API is deprecated and will be removed in a future version.
     *
     * - Migrate to [edit].
     */
    @Deprecated(message = "This function is deprecated due to performance issues. Migrate to the new usage", ReplaceWith("edit { put(prefs, value) }"))
    inline fun <reified T> put(prefs: PrefsData<T>, value: T) = edit { put(prefs, value) }

    /**
     * Creates a new [Editor].
     *
     * - Use this in the module environment or after [isUsingNativeStorage] is enabled.
     *
     * - The (Xposed) host environment is read-only, so this is unavailable there.
     * @return [Editor]
     */
    fun edit() = Editor()

    /**
     * Creates a new [Editor].
     *
     * Calls [Editor.apply] automatically.
     *
     * - Use this in the module environment or after [isUsingNativeStorage] is enabled.
     *
     * - The (Xposed) host environment is read-only, so this is unavailable there.
     * @param initiate the editing block.
     */
    fun edit(initiate: Editor.() -> Unit) = edit().apply(initiate).apply()

    /**
     * Clears key-value data cached in [YukiHookPrefsBridge].
     *
     * - This function and feature have been removed. They will be deleted in a future version.
     *
     * - Direct key-value caching has been removed because it can cause out-of-memory (OOM) issues.
     * @return [YukiHookPrefsBridge]
     */
    @Deprecated(message = "This function and feature have been removed. Delete this function")
    fun clearCache() {
    }

    /**
     * Storage proxy for [YukiHookPrefsBridge].
     *
     * - Use [edit] to obtain [Editor].
     *
     * - Use this in the module environment or after [isUsingNativeStorage] is enabled.
     *
     * - The (Xposed) host environment is read-only, so this is unavailable there.
     */
    inner class Editor internal constructor() {

        /** Creates storage proxies for all writable destinations. */
        private val editors = mutableListOf<SharedPreferences.Editor>().apply {
            if (isXposedEnvironment.not() || isUsingNativeStorage) {
                if (isUsingNativeStorage.not()) currentRemotePreferences().forEach { preferences ->
                    runCatching { preferences.edit() }.getOrNull()?.let { add(it) }
                }
                runCatching { currentSp.edit() }.getOrNull()?.let { add(it) }
            }
        }

        /**
         * Removes all stored data containing [key].
         * @param key the key name.
         * @return [Editor]
         */
        fun remove(key: String) = specifiedScope { editors.forEach { it.remove(key) } }

        /**
         * Removes the stored data for [PrefsData.key].
         * @param prefs the key-value instance.
         * @return [Editor]
         */
        inline fun <reified T> remove(prefs: PrefsData<T>) = remove(prefs.key)

        /**
         * Removes all stored data.
         * @return [Editor]
         */
        fun clear() = specifiedScope { editors.forEach { it.clear() } }

        /**
         * Stores a [String] value.
         *
         * - Using [PrefsData] to create a template and [put] to store data is recommended.
         * @param key the key name.
         * @param value the value data.
         * @return [Editor]
         */
        fun putString(key: String, value: String) = specifiedScope { editors.forEach { it.putString(key, value) } }

        /**
         * Stores a [Set] of [String] values.
         *
         * - Using [PrefsData] to create a template and [put] to store data is recommended.
         * @param key the key name.
         * @param value the value data.
         * @return [Editor]
         */
        fun putStringSet(key: String, value: Set<String>) = specifiedScope { editors.forEach { it.putStringSet(key, value) } }

        /**
         * Stores a [Boolean] value.
         *
         * - Using [PrefsData] to create a template and [put] to store data is recommended.
         * @param key the key name.
         * @param value the value data.
         * @return [Editor]
         */
        fun putBoolean(key: String, value: Boolean) = specifiedScope { editors.forEach { it.putBoolean(key, value) } }

        /**
         * Stores an [Int] value.
         *
         * - Using [PrefsData] to create a template and [put] to store data is recommended.
         * @param key the key name.
         * @param value the value data.
         * @return [Editor]
         */
        fun putInt(key: String, value: Int) = specifiedScope { editors.forEach { it.putInt(key, value) } }

        /**
         * Stores a [Float] value.
         *
         * - Using [PrefsData] to create a template and [put] to store data is recommended.
         * @param key the key name.
         * @param value the value data.
         * @return [Editor]
         */
        fun putFloat(key: String, value: Float) = specifiedScope { editors.forEach { it.putFloat(key, value) } }

        /**
         * Stores a [Long] value.
         *
         * - Using [PrefsData] to create a template and [put] to store data is recommended.
         * @param key the key name.
         * @param value the value data.
         * @return [Editor]
         */
        fun putLong(key: String, value: Long) = specifiedScope { editors.forEach { it.putLong(key, value) } }

        /**
         * Stores a value of the specified type intelligently.
         * @param prefs the key-value instance.
         * @param value the value to store. It can only be [String], [Set] of [String], [Int], [Float], [Long], or [Boolean].
         * @return [Editor]
         */
        inline fun <reified T> put(prefs: PrefsData<T>, value: T) = putPrefsData(prefs.key, value)

        /**
         * Stores a value of the specified type intelligently.
         *
         * Wrapper function for calling the inline function.
         * @param key the key.
         * @param value the value to store. It can only be [String], [Set] of [String], [Int], [Float], [Long], or [Boolean].
         * @return [Editor]
         */
        private fun putPrefsData(key: String, value: Any?) = when (value) {
            is String -> putString(key, value)
            is Set<*> -> putStringSet(key, value as? Set<String> ?: error("Key-Value type ${value.javaClass.name} is not allowed"))
            is Int -> putInt(key, value)
            is Float -> putFloat(key, value)
            is Long -> putLong(key, value)
            is Boolean -> putBoolean(key, value)
            else -> error("Key-Value type ${value?.javaClass?.name} is not allowed")
        }

        /**
         * Commits changes synchronously.
         * @return [Boolean] whether the operation succeeded.
         */
        fun commit() = makeWorldReadable {
            editors.map { editor -> runCatching { editor.commit() }.getOrNull() ?: false }.let { results ->
                results.isNotEmpty() && results.all { it }
            }
        }

        /** Applies changes asynchronously. */
        fun apply() = makeWorldReadable {
            editors.forEach { editor ->
                runCatching { editor.apply() }.onFailure { YLog.innerE("Failed to apply SharedPreferences changes", it) }
            }
        }

        /**
         * Executes only in the module environment or when [isUsingNativeStorage] is enabled.
         *
         * Using this outside the module environment prints a warning.
         * @param callback the callback to execute in the module environment.
         * @return [Editor]
         */
        private inline fun specifiedScope(callback: () -> Unit): Editor {
            if (isXposedEnvironment.not() || isUsingNativeStorage) callback()
            else YLog.innerW("YukiHookPrefsBridge.Editor not allowed in Xposed Environment")
            return this
        }
    }
}