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
package com.highcapable.yukihookapi.hook.dexkit.internal

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.highcapable.yukihookapi.hook.dexkit.cacheName
import com.highcapable.yukihookapi.hook.dexkit.cachePassword
import com.highcapable.yukihookapi.hook.param.PackageParam
import org.json.JSONArray
import org.luckypray.dexkit.wrap.DexClass
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.io.File
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/** Stores DexKit result descriptors and invalidates them when the hooked APK changes. */
internal class DexResolverCache(
    private val packageParam: PackageParam,
    private val classLoader: ClassLoader
) {

    private val memory = ConcurrentHashMap<String, List<String>>()
    private val preferencesLock = Any()
    private val fingerprint by lazy { createFingerprint() }
    private val preferencesName = cacheName
    private val cipher = cachePassword.takeIf(String::isNotEmpty)?.let(::DexResolverCipher)

    @Volatile
    private var checkedPreferences: SharedPreferences? = null

    fun getMethodList(key: String) = resolve(methodKey(key)) {
        DexMethod(it).getMethodInstance(classLoader).apply { isAccessible = true }
    }

    fun putMethodList(key: String, methods: List<Method>) =
        put(methodKey(key), methods.map { DexMethod(it).toString() })

    fun containsMethodList(key: String) = contains(methodKey(key))

    fun getConstructorList(key: String) = resolve(constructorKey(key)) {
        DexMethod(it).getConstructorInstance(classLoader).apply { isAccessible = true }
    }

    fun putConstructorList(key: String, constructors: List<Constructor<*>>) =
        put(constructorKey(key), constructors.map { DexMethod(it).toString() })

    fun containsConstructorList(key: String) = contains(constructorKey(key))

    fun getFieldList(key: String) = resolve(fieldKey(key)) {
        DexField(it).getFieldInstance(classLoader).apply { isAccessible = true }
    }

    fun putFieldList(key: String, fields: List<Field>) =
        put(fieldKey(key), fields.map { DexField(it).toString() })

    fun containsFieldList(key: String) = contains(fieldKey(key))

    fun getClassList(key: String) = resolve(classKey(key)) { DexClass(it).getInstance(classLoader) }

    fun putClassList(key: String, classes: List<Class<*>>) =
        put(classKey(key), classes.map { DexClass(it).toString() })

    fun containsClassList(key: String) = contains(classKey(key))

    fun clear() {
        memory.clear()
        preferences()?.edit {
            clear()
            putString(FINGERPRINT_KEY, encode(fingerprint))
        }
    }

    private inline fun <T> resolve(key: String, transform: (String) -> T): List<T>? {
        val descriptors = get(key) ?: return null
        return runCatching { descriptors.map(transform) }.getOrElse {
            remove(key)
            null
        }
    }

    private fun get(key: String): List<String>? {
        memory[key]?.let { return it }
        val preferences = preferences() ?: return null
        if (!preferences.contains(key)) return null
        val decoded = runCatching { decode(preferences.getString(key, null).orEmpty()) }.getOrElse {
            remove(key)
            return null
        }
        return decoded.also { memory[key] = it }
    }

    private fun contains(key: String) = memory.containsKey(key) || preferences()?.contains(key) == true

    private fun put(key: String, descriptors: List<String>) {
        val snapshot = descriptors.toList()
        memory[key] = snapshot
        preferences()?.edit {
            putString(key, encode(JSONArray(snapshot).toString()))
        }
    }

    private fun remove(key: String) {
        memory.remove(key)
        preferences()?.edit {
            remove(key)
        }
    }

    private fun preferences(): SharedPreferences? {
        val preferences = runCatching {
            val context = packageParam.appContext ?: packageParam.systemContext.let {
                if (packageParam.packageName == "android") it
                else it.createPackageContext(packageParam.packageName, Context.CONTEXT_IGNORE_SECURITY)
            }
            context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        }.getOrNull() ?: return null
        if (checkedPreferences === preferences) return preferences
        synchronized(preferencesLock) {
            if (checkedPreferences !== preferences) {
                val storedFingerprint = runCatching {
                    preferences.getString(FINGERPRINT_KEY, null)?.let(::decodeText)
                }.getOrNull()
                if (storedFingerprint != fingerprint) {
                    memory.clear()
                    preferences.edit {
                        clear()
                        putString(FINGERPRINT_KEY, encode(fingerprint))
                    }
                }
                checkedPreferences = preferences
            }
        }
        return preferences
    }

    private fun createFingerprint() = buildString {
        append(packageParam.packageName)
        buildList {
            packageParam.appInfo.sourceDir?.takeIf { it.isNotBlank() }?.let(::add)
            packageParam.appInfo.splitSourceDirs?.let(::addAll)
            packageParam.appInfo.splitPublicSourceDirs?.let(::addAll)
        }.distinct().sorted().forEach { path ->
            File(path).also {
                append('|').append(path).append(':').append(it.length()).append(':').append(it.lastModified())
            }
        }
    }

    private fun decode(value: String) = JSONArray(decodeText(value)).let { array ->
        List(array.length()) { index -> array.getString(index) }
    }

    private fun decodeText(value: String) = cipher?.decrypt(value) ?: value

    private fun encode(value: String) = cipher?.encrypt(value) ?: value

    private fun methodKey(key: String) = "method:$key"

    private fun constructorKey(key: String) = "constructor:$key"

    private fun fieldKey(key: String) = "field:$key"

    private fun classKey(key: String) = "class:$key"

    companion object {

        private const val FINGERPRINT_KEY = "@fingerprint"
    }
}