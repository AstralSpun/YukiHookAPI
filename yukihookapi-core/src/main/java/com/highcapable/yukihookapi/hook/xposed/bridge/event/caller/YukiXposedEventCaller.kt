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
 * This file is created by fankes on 2022/1/10.
 */
@file:Suppress("unused")

package com.highcapable.yukihookapi.hook.xposed.bridge.event.caller

import com.highcapable.yukihookapi.hook.xposed.bridge.event.YukiXposedEvent
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

/** Dispatches native libxposed lifecycle events to registered callbacks. */
internal object YukiXposedEventCaller {

    /** Dispatches the `onModuleLoaded` event. */
    internal fun callOnModuleLoaded(param: ModuleLoadedParam) {
        YukiXposedEvent.moduleLoadedCallback?.invoke(param)
    }

    /** Dispatches the `onPackageLoaded` event. */
    internal fun callOnPackageLoaded(param: PackageLoadedParam) {
        YukiXposedEvent.packageLoadedCallback?.invoke(param)
    }

    /** Dispatches the `onPackageReady` event. */
    internal fun callOnPackageReady(param: PackageReadyParam) {
        YukiXposedEvent.packageReadyCallback?.invoke(param)
    }

    /** Dispatches the `onSystemServerStarting` event. */
    internal fun callOnSystemServerStarting(param: SystemServerStartingParam) {
        YukiXposedEvent.systemServerStartingCallback?.invoke(param)
    }
}