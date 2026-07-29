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
 * This file is created by fankes on 2023/1/9.
 */
package com.highcapable.yukihookapi.hook.core.api.compat

import android.util.Log
import com.highcapable.yukihookapi.hook.core.api.factory.YukiHookCallbackDelegate
import com.highcapable.yukihookapi.hook.core.api.factory.callAfterHookedMember
import com.highcapable.yukihookapi.hook.core.api.factory.callBeforeHookedMember
import com.highcapable.yukihookapi.hook.core.api.priority.YukiHookPriority
import com.highcapable.yukihookapi.hook.core.api.proxy.YukiHookCallback
import com.highcapable.yukihookapi.hook.core.api.proxy.YukiMemberHook
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Member
import java.lang.reflect.Method

/**
 * Adapts core Hook operations to the active Hook API.
 */
internal object HookCompatHelper {

    /** Prefix reserved for Hook IDs managed by YukiHookAPI. */
    private const val HOOK_ID_PREFIX = "com.highcapable.yukihookapi:"

    /** Prefix for Hooks rebuilt by replaying the package callback. */
    private const val REPLAY_HOOK_ID_PREFIX = "${HOOK_ID_PREFIX}replay:"

    /** Prefix for Hooks installed after the replayable package callback has returned. */
    private const val DYNAMIC_HOOK_ID_PREFIX = "${HOOK_ID_PREFIX}dynamic:"

    /** Invocation frames active on the current thread. */
    private val invocationFrames = ThreadLocal<MutableList<InvocationState>>()

    /** Synchronizes deterministic Hook IDs and hot reload capture state. */
    private val hotReloadLock = Any()

    /** Next Hook index for each executable and priority in the current module generation. */
    private val hookIdIndexes = mutableMapOf<HookIdentity, Int>()

    /** Active Hooks that cannot be rebuilt by replaying the package callback. */
    private val dynamicHookHandles = mutableSetOf<XposedInterface.HookHandle>()

    /** Replayable Hook scope stack on the current thread. */
    private val replayScopes = ThreadLocal<MutableList<String>>()

    /** Deferred legacy Hook installations for each replayable scope on the current thread. */
    private val replayDeferredInstallations = ThreadLocal<MutableList<MutableList<() -> Unit>>>()

    /** Current capture session, or null during ordinary Hook installation. */
    private var hotReloadSession: HotReloadSession? = null

    /** Legacy Hook installations that have been dispatched but have not completed. */
    private var pendingHookInstallations = 0

    /** Hook installation scope used to determine whether a Hook can be rebuilt. */
    private enum class HookScope(val idPrefix: String) {
        REPLAY(REPLAY_HOOK_ID_PREFIX),
        DYNAMIC(DYNAMIC_HOOK_ID_PREFIX)
    }

    /** Key used to assign deterministic IDs to Hooks sharing one executable. */
    private data class HookIdentity(val scope: HookScope, val replayScope: String?, val executable: Executable, val priority: Int)

    /** A Hook captured from the new module generation before it is committed. */
    private class PendingHook(
        val executable: Executable,
        val priority: Int,
        val id: String,
        val hooker: XposedInterface.Hooker
    ) {

        var handle: XposedInterface.HookHandle? = null

        var isRemoved = false
    }

    /** Captured Hooks and native handles participating in one hot reload transaction. */
    private class HotReloadSession(oldHandles: List<XposedInterface.HookHandle>) {

        val oldHandles = oldHandles.toMutableList()
        val pendingHooks = mutableListOf<PendingHook>()
        val generationHandles = mutableSetOf<XposedInterface.HookHandle>()
        var isCapturing = true
    }

    /** Tracks native Hooks created directly through [XposedInterface] while a new generation is being initialized. */
    private class TrackingXposedInterface(
        private val base: XposedInterface,
        private val session: HotReloadSession
    ) : XposedInterface by base {

        override fun hook(origin: Executable) = TrackingHookBuilder(base.hook(origin), session)

        override fun hookClassInitializer(origin: Class<*>) = TrackingHookBuilder(base.hookClassInitializer(origin), session)
    }

    /** Preserves Hook builder chaining while recording its resulting native handle. */
    private class TrackingHookBuilder(
        private val base: XposedInterface.HookBuilder,
        private val session: HotReloadSession
    ) : XposedInterface.HookBuilder {

        override fun setPriority(priority: Int) = apply { base.setPriority(priority) }

        override fun setExceptionMode(mode: XposedInterface.ExceptionMode) = apply { base.setExceptionMode(mode) }

        override fun setId(id: String?) = apply { base.setId(id) }

        override fun intercept(hooker: XposedInterface.Hooker) = synchronized(hotReloadLock) {
            base.intercept(hooker).also { if (hotReloadSession === session) session.generationHandles += it }
        }
    }

    /**
     * Adapts a libxposed unhook handle for a hooked [Member].
     * @return [YukiMemberHook.HookedMember]
     */
    private fun XposedInterface.HookHandle.compat(id: String) =
        YukiHookCallbackDelegate.createHookedMemberCallback(
            member = { executable },
            onRemove = {
                try {
                    unhook()
                } finally {
                    if (id.startsWith(DYNAMIC_HOOK_ID_PREFIX))
                        synchronized(hotReloadLock) { dynamicHookHandles -= this }
                }
            }
        )

    /** Adapts a captured Hook before and after its native handle is committed. */
    private fun PendingHook.compat() =
        YukiHookCallbackDelegate.createHookedMemberCallback(
            member = { executable },
            onRemove = {
                isRemoved = true
                handle?.unhook()
            }
        )

    /**
     * Mutable invocation state used to adapt libxposed's interceptor chain to Yuki's before/after callbacks.
     */
    private class InvocationState(
        private val chain: XposedInterface.Chain,
        private val hookerToken: Any
    ) {

        private var isAfterCallback = false

        private var isSkipped = false

        private var isProceeding = false

        private var result: Any? = null

        private var throwable: Throwable? = null

        private val args = chain.args.toTypedArray()

        private lateinit var invocationToken: Any

        private val param = YukiHookCallbackDelegate.createParamCallback(
            member = { chain.executable },
            instance = { chain.thisObject },
            args = { args },
            hasThrowable = { throwable != null },
            result = { value, assign ->
                if (assign) {
                    if (isAfterCallback.not()) isSkipped = true
                    result = value
                    throwable = null
                }
                result
            },
            throwable = { value, assign ->
                if (assign) {
                    if (isAfterCallback.not()) isSkipped = true
                    throwable = value
                    result = null
                }
                throwable
            }
        )

        /** Gets whether this frame can be the parent Yuki interceptor of [child]. */
        private fun canParent(child: InvocationState) =
            isProceeding && hookerToken !== child.hookerToken && chain.executable == child.chain.executable &&
                chain.thisObject === child.chain.thisObject

        /** Synchronizes arguments changed by a downstream Yuki interceptor. */
        private fun syncArgsFrom(child: InvocationState) {
            if (args.size == child.args.size) child.args.copyInto(args)
        }

        fun invoke(callback: YukiHookCallback): Any? {
            val frames = invocationFrames.get() ?: mutableListOf<InvocationState>().also { invocationFrames.set(it) }
            val candidate = frames.lastOrNull()?.takeIf { it.canParent(this) }
            val parent = candidate?.takeUnless { parent ->
                frames.any { it.invocationToken === parent.invocationToken && it.hookerToken === hookerToken }
            }
            invocationToken = parent?.invocationToken ?: Any()
            frames.add(this)
            return try {
                callback.callBeforeHookedMember(param)
                if (isSkipped.not()) runCatching {
                    isProceeding = true
                    try {
                        val thisObject = chain.thisObject
                        if (thisObject == null) chain.proceed(args) else chain.proceedWith(thisObject, args)
                    } finally {
                        isProceeding = false
                    }
                }.onSuccess {
                    result = it
                    throwable = null
                }.onFailure {
                    result = null
                    throwable = it
                }
                isAfterCallback = true
                callback.callAfterHookedMember(param)
                throwable?.let { throw it }
                result
            } finally {
                parent?.syncArgsFrom(this)
                frames.removeAt(frames.lastIndex)
                if (frames.isEmpty()) invocationFrames.remove()
            }
        }
    }

    /**
     * Adapts a [YukiHookCallback] to the native Hook API callback.
     * @return [Any] the native callback.
     */
    private fun YukiHookCallback.compat() = when (HookApiCategoryHelper.currentCategory) {
        HookApiCategory.LIBXPOSED -> Any().let { hookerToken ->
            XposedInterface.Hooker { chain -> InvocationState(chain, hookerToken).invoke(this) }
        }
        HookApiCategory.UNKNOWN -> throwUnsupportedHookApiError()
    }

    /** Gets the native priority corresponding to this Yuki priority. */
    private fun YukiHookPriority.compat() = when (this) {
        YukiHookPriority.DEFAULT -> XposedInterface.PRIORITY_DEFAULT
        YukiHookPriority.LOWEST -> XposedInterface.PRIORITY_LOWEST
        YukiHookPriority.HIGHEST -> XposedInterface.PRIORITY_HIGHEST
    }

    /** Whether new Yuki Hooks are currently being captured for hot reload. */
    internal val isHotReloadCapturing get() = synchronized(hotReloadLock) { hotReloadSession?.isCapturing == true }

    /** Wraps the framework interface when native Handles must participate in the current hot reload transaction. */
    internal fun trackHotReloadHandles(base: XposedInterface) = synchronized(hotReloadLock) {
        hotReloadSession?.let { TrackingXposedInterface(base, it) } ?: base
    }

    /** Runs [block] in a named scope that can be replayed after hot reload. */
    internal fun <T> withHotReloadReplay(scope: String, block: () -> T): T {
        val scopes = replayScopes.get() ?: mutableListOf<String>().also { replayScopes.set(it) }
        val deferredFrames = replayDeferredInstallations.get()
            ?: mutableListOf<MutableList<() -> Unit>>().also { replayDeferredInstallations.set(it) }
        val deferred = mutableListOf<() -> Unit>()
        scopes += scope
        deferredFrames += deferred
        return try {
            block().also {
                var index = 0
                while (index < deferred.size) deferred[index++]()
            }
        } finally {
            deferredFrames.removeAt(deferredFrames.lastIndex)
            scopes.removeAt(scopes.lastIndex)
            if (deferredFrames.isEmpty()) replayDeferredInstallations.remove()
            if (scopes.isEmpty()) replayScopes.remove()
        }
    }

    /** Defers a legacy installation until its result listeners have been configured by the replayed DSL. */
    internal fun deferHotReloadInstallation(block: () -> Unit) {
        check(isHotReloadCapturing) { "A YukiHookAPI hot reload capture is not active" }
        val deferred = replayDeferredInstallations.get()?.lastOrNull()
            ?: error("A replayable YukiHookAPI scope is not active")
        deferred += block
    }

    /** Rejects hot reload while active Yuki Hooks exist outside a replayable package callback. */
    internal fun ensureHotReloadable() {
        val (dynamicHooks, pendingInstallations) = synchronized(hotReloadLock) {
            dynamicHookHandles.map { it.executable }.distinct() to pendingHookInstallations
        }
        check(pendingInstallations == 0) {
            "YukiHookAPI cannot hot reload while $pendingInstallations asynchronous Hook installation(s) are still running"
        }
        check(dynamicHooks.isEmpty()) {
            "YukiHookAPI cannot hot reload while runtime Hooks are active outside the replayable package callback: $dynamicHooks"
        }
    }

    /** Marks an asynchronous legacy Hook installation as active. */
    internal fun beginHookInstallation() {
        synchronized(hotReloadLock) { pendingHookInstallations++ }
    }

    /** Marks an asynchronous legacy Hook installation as complete. */
    internal fun finishHookInstallation() {
        synchronized(hotReloadLock) {
            check(pendingHookInstallations > 0) { "No asynchronous YukiHookAPI Hook installation is active" }
            pendingHookInstallations--
        }
    }

    /** Starts capturing new-generation Hooks and resets deterministic Hook ordering. */
    internal fun beginHotReload(oldHandles: List<XposedInterface.HookHandle>) {
        synchronized(hotReloadLock) {
            check(hotReloadSession == null) { "A YukiHookAPI hot reload session is already active" }
            hookIdIndexes.clear()
            hotReloadSession = HotReloadSession(oldHandles.toList())
        }
    }

    /**
     * Commits all captured Hooks, replacing matching old handles before removing obsolete Hooks.
     *
     * All new callbacks are captured before this method starts changing the active Hook chain.
     */
    internal fun commitHotReload() {
        val session = synchronized(hotReloadLock) {
            hotReloadSession ?: error("No YukiHookAPI hot reload session is active")
        }
        val oldReplayHandles = session.oldHandles.filter { it.id?.startsWith(REPLAY_HOOK_ID_PREFIX) == true }
        val oldLegacyYukiHandles = session.oldHandles.filter { handle ->
            handle.id?.let { it.startsWith(HOOK_ID_PREFIX) && it.startsWith(REPLAY_HOOK_ID_PREFIX).not() &&
                it.startsWith(DYNAMIC_HOOK_ID_PREFIX).not() } == true
        }
        val oldForeignHandles = session.oldHandles.filterNot { it.id?.startsWith(HOOK_ID_PREFIX) == true }
        check(session.oldHandles.none { it.id?.startsWith(DYNAMIC_HOOK_ID_PREFIX) == true }) {
            "YukiHookAPI cannot commit hot reload while old runtime Hooks are still active"
        }
        val claimedOldHandles = mutableSetOf<XposedInterface.HookHandle>()
        try {
            session.pendingHooks.filterNot { it.isRemoved }.forEach { pending ->
                val oldHandle = oldReplayHandles.firstOrNull {
                    it !in claimedOldHandles && it.id == pending.id && it.executable == pending.executable
                }
                pending.handle = oldHandle?.replaceHook(pending.hooker)
                    ?: HookApiCategoryHelper.base.hook(pending.executable)
                        .setPriority(pending.priority)
                        .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH)
                        .setId(pending.id)
                        .intercept(pending.hooker)
                if (oldHandle != null) {
                    claimedOldHandles += oldHandle
                    synchronized(hotReloadLock) {
                        if (hotReloadSession === session) pending.handle?.also { session.generationHandles += it }
                    }
                }
            }
            oldReplayHandles.filterNot { it in claimedOldHandles }.forEach { it.unhook() }
            oldLegacyYukiHandles.forEach { it.unhook() }
            oldForeignHandles.forEach { oldHandle ->
                try {
                    oldHandle.unhook()
                } catch (_: IllegalStateException) {
                    // A new native Hook with the same ID may already have replaced this handle.
                }
            }
            synchronized(hotReloadLock) {
                if (hotReloadSession === session) session.isCapturing = false
            }
        } catch (throwable: Throwable) {
            throw throwable
        }
    }

    /** Completes a successful transaction and releases all references to previous-generation Handles and callbacks. */
    internal fun finishHotReload() {
        synchronized(hotReloadLock) {
            hotReloadSession?.also {
                it.oldHandles.clear()
                it.pendingHooks.clear()
                it.generationHandles.clear()
            }
            hotReloadSession = null
        }
    }

    /** Fails a transaction closed by removing both new-generation and previous-generation Hooks. */
    internal fun abortHotReload(fallbackOldHandles: List<XposedInterface.HookHandle> = emptyList()) {
        val (generationHandles, oldHandles) = synchronized(hotReloadLock) {
            val session = hotReloadSession
            hotReloadSession = null
            val currentHandles = session?.generationHandles?.toList().orEmpty()
            val previousHandles = session?.oldHandles?.toList() ?: fallbackOldHandles.toList()
            dynamicHookHandles.removeAll(currentHandles.toSet())
            session?.oldHandles?.clear()
            session?.pendingHooks?.clear()
            session?.generationHandles?.clear()
            currentHandles to previousHandles
        }
        generationHandles.forEach { runCatching { it.unhook() } }
        oldHandles.forEach { runCatching { it.unhook() } }
    }

    /** Reserves a deterministic Hook ID before an asynchronous legacy Hook is dispatched. */
    internal fun reserveHookId(member: Member, priority: YukiHookPriority): String {
        val executable = member as? Executable ?: error("Only methods and constructors can be hooked: $member")
        return nextHookId(executable, priority.compat())
    }

    /** Assigns a deterministic Hook ID within the current module generation. */
    private fun nextHookId(executable: Executable, priority: Int): String = synchronized(hotReloadLock) {
        val replayScope = replayScopes.get()?.lastOrNull()
        val scope = if (replayScope == null) HookScope.DYNAMIC else HookScope.REPLAY
        val identity = HookIdentity(scope, replayScope, executable, priority)
        val index = hookIdIndexes[identity] ?: 0
        hookIdIndexes[identity] = index + 1
        "${scope.idPrefix}${replayScope?.let { "${it.length}:$it:" } ?: ""}$priority:$index"
    }

    /**
     * Hook [Member]
     * @param member the method or constructor to Hook.
     * @param callback the Hook callback.
     * @return [YukiMemberHook.HookedMember] or null.
     */
    internal fun hookMember(member: Member?, callback: YukiHookCallback, reservedId: String? = null): YukiMemberHook.HookedMember? {
        if (member == null) return null
        return when (HookApiCategoryHelper.currentCategory) {
            HookApiCategory.LIBXPOSED -> {
                val executable = member as? Executable ?: error("Only methods and constructors can be hooked: $member")
                val priority = callback.priority.compat()
                val id = reservedId ?: nextHookId(executable, priority)
                check(id.startsWith(HOOK_ID_PREFIX)) { "Invalid reserved YukiHookAPI Hook ID: $id" }
                val hooker = callback.compat()
                val pending = synchronized(hotReloadLock) {
                    hotReloadSession?.takeIf { it.isCapturing }?.let { session ->
                        check(id.startsWith(REPLAY_HOOK_ID_PREFIX)) {
                            "Hooks installed outside the replayable package callback cannot be captured for hot reload"
                        }
                        PendingHook(executable, priority, id, hooker).also { session.pendingHooks += it }
                    }
                }
                pending?.compat() ?: HookApiCategoryHelper.base
                    .hook(executable)
                    .setPriority(priority)
                    .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH)
                    .setId(id)
                    .intercept(hooker)
                    .also { if (id.startsWith(DYNAMIC_HOOK_ID_PREFIX)) synchronized(hotReloadLock) { dynamicHookHandles += it } }
                    .compat(id)
            }
            HookApiCategory.UNKNOWN -> throwUnsupportedHookApiError()
        }
    }

    /**
     * Invokes the original unhooked [Member].
     * @param member the member instance.
     * @param args the argument array.
     * @return [Any] or null.
     */
    internal fun invokeOriginalMember(member: Member?, instance: Any?, args: Array<out Any?>?): Any? {
        if (member == null) return null
        return when (HookApiCategoryHelper.currentCategory) {
            HookApiCategory.LIBXPOSED -> invokeOriginalMember(member, instance, args ?: emptyArray())
            HookApiCategory.UNKNOWN -> throwUnsupportedHookApiError()
        }
    }

    /** Invokes a member through an origin-only libxposed invoker. */
    @Suppress("UNCHECKED_CAST")
    private fun invokeOriginalMember(member: Member, instance: Any?, args: Array<out Any?>): Any? = try {
        when (member) {
            is Method -> HookApiCategoryHelper.base.getInvoker(member)
                .setType(XposedInterface.Invoker.Type.ORIGIN)
                .invoke(instance, *args)
            is Constructor<*> -> HookApiCategoryHelper.base.getInvoker(member as Constructor<Any>)
                .setType(XposedInterface.Invoker.Type.ORIGIN)
                .invoke(instance, *args)
            else -> error("Only methods and constructors can be invoked: $member")
        }
    } catch (e: InvocationTargetException) {
        throw e.targetException ?: e
    }

    /**
     * Prints through the active Hook API logger.
     * @param msg the log message.
     * @param e the exception stack trace, defaults to null.
     */
    internal fun logByHooker(msg: String, e: Throwable? = null) {
        when (HookApiCategoryHelper.currentCategory) {
            HookApiCategory.LIBXPOSED -> if (e == null)
                HookApiCategoryHelper.base.log(Log.INFO, "YukiHookAPI", msg)
            else HookApiCategoryHelper.base.log(Log.ERROR, "YukiHookAPI", msg, e)
            HookApiCategory.UNKNOWN -> throwUnsupportedHookApiError()
        }
    }

    /** Throws an error for an unsupported Hook API. */
    private fun throwUnsupportedHookApiError(): Nothing =
        error("YukiHookAPI cannot support current Hook API or cannot found any available Hook APIs in current environment")
}