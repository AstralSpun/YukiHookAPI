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
@file:Suppress("unused")

package com.highcapable.yukihookapi.hook.xposed.bridge.caller

import android.content.pm.ApplicationInfo
import android.os.Bundle
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.xposed.bridge.YukiXposedModule
import com.highcapable.yukihookapi.hook.xposed.bridge.resources.YukiResources
import com.highcapable.yukihookapi.hook.xposed.bridge.type.HookEntryType
import io.github.libxposed.api.XposedInterface

/**
 * Routes calls to the Xposed module lifecycle implementation.
 */
internal object YukiXposedModuleCaller {

    /**
     * Gets whether the module has loaded its Xposed callback.
     * @return [Boolean]
     */
    internal val isXposedCallbackSetUp get() = YukiXposedModule.isXposedCallbackSetUp

    /**
     * Signals that the Xposed module started loading.
     * @param base the framework interface attached to the module entry.
     * @param packageName the current Xposed module package name.
     * @param appFilePath the current Xposed module APK path.
     */
    internal fun callOnStartLoadModule(base: XposedInterface, packageName: String, appFilePath: String) =
        YukiXposedModule.onStartLoadModule(base, packageName, appFilePath)

    /**
     * Signals that the Xposed module finished loading.
     */
    internal fun callOnFinishLoadModule() = YukiXposedModule.onFinishLoadModule()

    /** Returns whether the requested hot reload is enabled for the current generation. */
    internal fun callIsHotReloadAllowed(extras: Bundle?) = YukiXposedModule.isHotReloadAllowed(extras)

    /** Removes internal request metadata before extras are exposed to module code. */
    internal fun callSanitizeHotReloadExtras(extras: Bundle?) = YukiXposedModule.sanitizeHotReloadExtras(extras)

    /** Validates hot reload and captures process state owned by the old module generation. */
    internal fun callOnHotReloading(inheritedState: Any?) = YukiXposedModule.onHotReloading(inheritedState)

    /** Releases YukiHookAPI-owned external callbacks after module cleanup accepts hot reload. */
    internal fun callOnHotReloadingAccepted() = YukiXposedModule.onHotReloadingAccepted()

    /** Starts tracking native Handles created by the incoming module generation. */
    internal fun callOnStartHotReload(oldHookHandles: List<XposedInterface.HookHandle>) =
        YukiXposedModule.onStartHotReload(oldHookHandles)

    /** Replays package state and commits new-generation Yuki Hooks. */
    internal fun callOnHotReloaded(savedInstanceState: Any?) = YukiXposedModule.onHotReloaded(savedInstanceState)

    /** Releases previous-generation transaction state after a successful hot reload. */
    internal fun callOnFinishHotReload() = YukiXposedModule.onFinishHotReload()

    /** Removes all Hooks participating in a failed hot reload transaction. */
    internal fun callOnAbortHotReload(oldHookHandles: List<XposedInterface.HookHandle>) =
        YukiXposedModule.onAbortHotReload(oldHookHandles)

    /**
     * Signals that an available host app started loading.
     * @param type the current Hook entry type.
     * @param packageName the host package name.
     * @param processName the host process name.
     * @param appClassLoader the host [ClassLoader].
     * @param appInfo the host [ApplicationInfo].
     * @param appResources the host [YukiResources].
     */
    internal fun callOnPackageLoaded(
        type: HookEntryType,
        packageName: String?,
        processName: String? = "",
        appClassLoader: ClassLoader? = null,
        appInfo: ApplicationInfo? = null,
        appResources: YukiResources? = null
    ) = YukiXposedModule.onPackageLoaded(type, packageName, processName, appClassLoader, appInfo, appResources)

    /**
     * Prints an error-level log entry.
     * @param msg the log message.
     * @param e the exception stack trace, defaults to null.
     */
    internal fun callLogError(msg: String, e: Throwable? = null) = YLog.innerE(msg, e)
}