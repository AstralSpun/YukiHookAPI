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

import com.highcapable.yukihookapi.hook.param.PackageParam
import org.luckypray.dexkit.DexKitBridge
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Manages the DexKit bridge associated with one hooked application. */
internal class DexResolverRuntime private constructor(
    val packageParam: PackageParam,
    val classLoader: ClassLoader
) {

    private val lock = ReentrantLock()
    private var bridge: DexKitBridge? = null
    private var apkPath: String? = null
    private var activeQueries = 0
    private var closeRequested = false
    private var idleClosePending = false
    private var closeFuture: ScheduledFuture<*>? = null
    private var closeGeneration = 0L

    val cache = DexResolverCache(packageParam, classLoader)

    /** Creates the bridge from a custom APK path if it has not already been created. */
    fun create(apkPath: String) {
        require(apkPath.isNotBlank()) { "The APK path cannot be blank" }
        lock.withLock {
            ensureNotClosingLocked()
            if (bridge?.isValid == true) return
            this.apkPath = apkPath
            bridge = runCatching(::createBridge).onFailure { this.apkPath = null }.getOrThrow()
        }
    }

    /** Returns the underlying bridge and resets its idle timeout. */
    fun getBridge(): DexKitBridge = lock.withLock {
        ensureNotClosingLocked()
        cancelCloseLocked()
        obtainBridgeLocked().also { scheduleCloseLocked() }
    }

    /** Runs a query while preventing idle cleanup from closing the bridge. */
    fun <T> withBridge(block: (DexKitBridge) -> T): T {
        val current = lock.withLock {
            ensureNotClosingLocked()
            cancelCloseLocked()
            obtainBridgeLocked().also { activeQueries++ }
        }
        return try {
            block(current)
        } finally {
            lock.withLock {
                activeQueries--
                if (activeQueries == 0 && (closeRequested || idleClosePending)) closeBridgeLocked()
                else if (activeQueries == 0) scheduleCloseLocked()
            }
        }
    }

    /** Closes the bridge immediately, or after the current queries finish. */
    fun close() = lock.withLock {
        cancelCloseLocked()
        if (activeQueries == 0) closeBridgeLocked() else closeRequested = true
    }

    private fun obtainBridgeLocked() = bridge?.takeIf { it.isValid } ?: createBridge().also { bridge = it }

    private fun ensureNotClosingLocked() {
        check(!closeRequested) { "The DexKit bridge is waiting for active queries to finish before closing" }
    }

    private fun createBridge(): DexKitBridge {
        loadLibrary()
        apkPath?.let { return DexKitBridge.create(it) }
        return runCatching { DexKitBridge.create(classLoader, true) }.getOrElse { throwable ->
            packageParam.appInfo.sourceDir?.takeIf { it.isNotBlank() }?.let(DexKitBridge::create) ?: throw throwable
        }
    }

    private fun scheduleCloseLocked() {
        cancelCloseLocked()
        val delay = autoCloseTime
        if (delay <= 0) return
        val generation = closeGeneration
        closeFuture = scheduler.schedule({
            lock.withLock {
                if (generation != closeGeneration) return@withLock
                closeFuture = null
                closeGeneration++
                if (activeQueries == 0) closeBridgeLocked() else idleClosePending = true
            }
        }, delay, TimeUnit.MILLISECONDS)
    }

    private fun cancelCloseLocked() {
        closeGeneration++
        closeFuture?.cancel(false)
        closeFuture = null
        idleClosePending = false
    }

    private fun closeBridgeLocked() {
        bridge?.let { runCatching { it.close() } }
        bridge = null
        apkPath = null
        closeRequested = false
        idleClosePending = false
    }

    companion object {

        private data class RuntimeKey(val packageName: String, val classLoader: ClassLoader)

        private val runtimes = ConcurrentHashMap<RuntimeKey, DexResolverRuntime>()
        private val currentRuntime = ThreadLocal<DexResolverRuntime>()

        private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "YukiHookAPI-DexResolver").apply { isDaemon = true }
        }
        private val libraryLock = Any()

        val isLoadLibrary = AtomicBoolean(false)

        @Volatile
        var autoCloseTime = 10_000L

        /** Gets the runtime bound to the current hooked application. */
        fun obtain(packageParam: PackageParam): DexResolverRuntime {
            val classLoader = packageParam.appClassLoader
                ?: error("The hooked application's ClassLoader is not available")
            val key = RuntimeKey(packageParam.packageName, classLoader)
            return runtimes.computeIfAbsent(key) { DexResolverRuntime(packageParam, classLoader) }.also(currentRuntime::set)
        }

        /** Gets the runtime bound through a [PackageParam.DexResolver] scope. */
        fun current() = currentOrNull() ?: error("Access PackageParam.DexResolver before executing an unbound finder")

        /** Gets the runtime bound to this thread, or the process runtime when only one exists. */
        fun currentOrNull() = currentRuntime.get() ?: runtimes.values.singleOrNull()

        private fun loadLibrary() {
            if (isLoadLibrary.get()) return
            synchronized(libraryLock) {
                if (isLoadLibrary.get()) return
                System.loadLibrary("dexkit")
                isLoadLibrary.set(true)
            }
        }
    }
}