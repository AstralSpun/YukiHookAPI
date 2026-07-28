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

    /** Invocation frames active on the current thread. */
    private val invocationFrames = ThreadLocal<MutableList<InvocationState>>()

    /**
     * Adapts a libxposed unhook handle for a hooked [Member].
     * @return [YukiMemberHook.HookedMember]
     */
    private fun XposedInterface.HookHandle.compat() =
        YukiHookCallbackDelegate.createHookedMemberCallback(
            member = { executable },
            onRemove = { unhook() }
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
                        chain.thisObject?.let { chain.proceedWith(it, args) } ?: chain.proceed(args)
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

    /**
     * Hook [Member]
     * @param member the method or constructor to Hook.
     * @param callback the Hook callback.
     * @return [YukiMemberHook.HookedMember] or null.
     */
    internal fun hookMember(member: Member?, callback: YukiHookCallback): YukiMemberHook.HookedMember? {
        if (member == null) return null
        return when (HookApiCategoryHelper.currentCategory) {
            HookApiCategory.LIBXPOSED -> HookApiCategoryHelper.base
                .hook(member as? Executable ?: error("Only methods and constructors can be hooked: $member"))
                .setPriority(callback.priority.compat())
                .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH)
                .intercept(callback.compat())
                .compat()
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