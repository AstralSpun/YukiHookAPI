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
package com.highcapable.yukihookapi.hook.dexkit

import com.highcapable.yukihookapi.hook.dexkit.bean.ClassInfo
import com.highcapable.yukihookapi.hook.dexkit.bean.ConstructorInfo
import com.highcapable.yukihookapi.hook.dexkit.bean.FieldInfo
import com.highcapable.yukihookapi.hook.dexkit.bean.MethodInfo
import com.highcapable.yukihookapi.hook.dexkit.internal.DexResolverRuntime
import com.highcapable.yukihookapi.hook.param.PackageParam
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gets a DexKit resolver bound to the current hooked application.
 *
 * This property is available in [PackageParam] scopes, including `encase`, `loadApp`, and `YukiBaseHooker`.
 */
@Suppress("PropertyName")
val PackageParam.DexResolver get() = DexResolverScope(this)

/** Name of the directory under the hooked application's files directory used by the DexKit cache. */
var folderName = "DexResolver"

/** Base name of the FastKV files used by the DexKit cache. */
var cacheName = "DexSearchCache"

/** Password used to encrypt the DexKit cache. An empty password keeps the cache unencrypted. */
var cachePassword = ""

/** DexKit query entry point bound to a [PackageParam]. */
class DexResolverScope internal constructor(private val packageParam: PackageParam) {

    private val runtime = DexResolverRuntime.obtain(packageParam)

    /** Whether the DexKit native library has been loaded in the current process. */
    val isLoadLibrary: AtomicBoolean get() = DexResolverRuntime.isLoadLibrary

    /**
     * Sets how long an idle DexKit bridge remains open.
     *
     * Set this to `0` or a negative value to disable automatic closing. The default is 10 seconds.
     * @param time timeout in milliseconds.
     */
    fun setAutoCloseTime(time: Long) {
        DexResolverRuntime.autoCloseTime = time
    }

    /** Creates the DexKit bridge from a custom APK path. */
    fun create(apkPath: String) = runtime.create(apkPath)

    /**
     * Gets the underlying DexKit bridge.
     *
     * Prefer [queryMethod], [queryField], or [queryClass] for long-running queries because those calls keep the bridge open until the query finishes.
     */
    fun getDexKitBridge(): DexKitBridge = runtime.getBridge()

    /** Creates a method finder using the XPHelper-compatible DSL. */
    @JvmSynthetic
    fun findMethod(methodInfo: MethodInfo.() -> Unit): MethodFinder =
        MethodInfo().apply(methodInfo).generate(packageParam, runtime)

    /** Creates a constructor finder using the DexKit DSL. */
    @JvmSynthetic
    fun findConstructor(constructorInfo: ConstructorInfo.() -> Unit): ConstructorFinder =
        ConstructorInfo().apply(constructorInfo).generate(packageParam, runtime)

    /** Creates a field finder using the XPHelper-compatible DSL. */
    @JvmSynthetic
    fun findField(fieldInfo: FieldInfo.() -> Unit): FieldFinder =
        FieldInfo().apply(fieldInfo).generate(runtime)

    /** Creates a class finder using the XPHelper-compatible DSL. */
    @JvmSynthetic
    fun findClass(classInfo: ClassInfo.() -> Unit): ClassFinder =
        ClassInfo().apply(classInfo).generate(runtime)

    /** Runs and caches a custom DexKit method query. */
    fun queryMethod(key: String, block: (DexKitBridge) -> List<Method>): List<Method> {
        runtime.cache.getMethodList("query:$key")?.let { return it }
        return runtime.withBridge(block).also { runtime.cache.putMethodList("query:$key", it) }
    }

    /** Runs and caches a custom DexKit field query. */
    fun queryField(key: String, block: (DexKitBridge) -> List<Field>): List<Field> {
        runtime.cache.getFieldList("query:$key")?.let { return it }
        return runtime.withBridge(block).also { runtime.cache.putFieldList("query:$key", it) }
    }

    /** Runs and caches a custom DexKit class query. */
    fun queryClass(key: String, block: (DexKitBridge) -> List<Class<*>>): List<Class<*>> {
        runtime.cache.getClassList("query:$key")?.let { return it }
        return runtime.withBridge(block).also { runtime.cache.putClassList("query:$key", it) }
    }

    /** Runs and caches a custom DexKit constructor query. */
    fun queryConstructor(key: String, block: (DexKitBridge) -> List<Constructor<*>>): List<Constructor<*>> {
        runtime.cache.getConstructorList("query:$key")?.let { return it }
        return runtime.withBridge(block).also { runtime.cache.putConstructorList("query:$key", it) }
    }

    /** Clears all cached DexKit results for the current hooked application. */
    fun clearCache() = runtime.cache.clear()

    /** Releases the DexKit bridge for the current hooked application. */
    fun close() = runtime.close()
}