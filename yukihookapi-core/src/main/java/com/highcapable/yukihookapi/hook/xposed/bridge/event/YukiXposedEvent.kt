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
 * This file is created by fankes on 2022/4/30.
 * This file is modified by fankes on 2022/1/10.
 */
@file:Suppress("unused")

package com.highcapable.yukihookapi.hook.xposed.bridge.event

import com.highcapable.yukihookapi.hook.core.api.compat.HookApiCategoryHelper
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

/**
 * Registers listeners for native libxposed module lifecycle events.
 */
object YukiXposedEvent {

    /**
     * Gets the libxposed interface attached to the generated module entry.
     *
     * This is available after the framework invokes `onModuleLoaded`.
     * @return [XposedInterface]
     */
    val xposedInterface get() = HookApiCategoryHelper.base

    /** Callback invoked when the module is loaded into a process. */
    internal var moduleLoadedCallback: ((ModuleLoadedParam) -> Unit)? = null

    /** Callback invoked when a package's default class loader is available. */
    internal var packageLoadedCallback: ((PackageLoadedParam) -> Unit)? = null

    /** Callback invoked when a package's final class loader is ready. */
    internal var packageReadyCallback: ((PackageReadyParam) -> Unit)? = null

    /** Callback invoked when system_server begins starting services. */
    internal var systemServerStartingCallback: ((SystemServerStartingParam) -> Unit)? = null

    /** Configures [YukiXposedEvent]. */
    inline fun events(initiate: YukiXposedEvent.() -> Unit) {
        YukiXposedEvent.apply(initiate)
    }

    /** Sets the `onModuleLoaded` event listener. */
    fun onModuleLoaded(result: (ModuleLoadedParam) -> Unit) {
        moduleLoadedCallback = result
    }

    /** Sets the `onPackageLoaded` event listener. */
    fun onPackageLoaded(result: (PackageLoadedParam) -> Unit) {
        packageLoadedCallback = result
    }

    /** Sets the `onPackageReady` event listener. */
    fun onPackageReady(result: (PackageReadyParam) -> Unit) {
        packageReadyCallback = result
    }

    /** Sets the `onSystemServerStarting` event listener. */
    fun onSystemServerStarting(result: (SystemServerStartingParam) -> Unit) {
        systemServerStartingCallback = result
    }
}