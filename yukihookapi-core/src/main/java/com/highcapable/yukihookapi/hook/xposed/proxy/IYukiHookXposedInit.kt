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
 * This file is created by fankes on 2022/2/2.
 * This file is modified by fankes on 2022/4/22.
 */
@file:Suppress("unused")

package com.highcapable.yukihookapi.hook.xposed.proxy

import android.os.Bundle
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.xposed.bridge.event.YukiXposedEvent

/**
 * Xposed loading entry point for [YukiHookAPI].
 *
 * - Annotate the implementation with [InjectYukiHookWithXposed] to mark the module Hook entry.
 *
 * [onInit] is called automatically while [YukiHookAPI] initializes.
 *
 * [onHook] is called automatically when Hook loading starts.
 *
 * Call [YukiHookAPI.configs] or [configs] from [onInit].
 *
 * Call [YukiHookAPI.encase] or [encase] from [onHook].
 *
 * Override [onXposedEvent] to listen for native Xposed API events.
 *
 * See [IYukiHookXposedInit Interface](https://highcapable.github.io/YukiHookAPI/en/config/xposed-using#iyukihookxposedinit-interface)
 */
interface IYukiHookXposedInit {

    /**
     * Configures [YukiHookAPI.Configs] during initialization.
     *
     * - Perform initialization only. Do not run Hook operations here.
     *
     * This method is optional when no custom configuration is required.
     */
    fun onInit() {}

    /**
     * Starts module Hook loading.
     *
     * Xposed API
     *
     * Call [YukiHookAPI.encase] or [encase] to start Hook operations.
     */
    fun onHook()

    /**
     * Called in the old module generation before an accepted hot reload begins.
     *
     * Use this callback to stop module-owned threads, unregister callbacks and native hooks, and release references that
     * cannot be managed by [YukiHookAPI]. Throwing an exception rejects the hot reload request.
     *
     * Yuki Hooks created in replayable package callbacks are captured and replaced automatically. Runtime Yuki Hooks created
     * later from app lifecycle, receiver, or class-load callbacks and Activity Proxy integration reject hot reload while active.
     * @param extras class-loader-neutral data supplied by the manual hot reload request, or null for an automatic request.
     */
    fun onHotReloading(extras: Bundle?) {}

    /**
     * Called in the new module generation after [YukiHookAPI] has rebuilt and committed its hooks.
     *
     * Use this callback to restore module-owned threads, callbacks, native hooks, and other non-Hook resources.
     * Throwing marks the request as failed and removes Hooks from both generations; clean up any partially restored
     * module-owned resources before allowing an exception to escape.
     * @param extras class-loader-neutral data supplied by the manual hot reload request, or null for an automatic request.
     */
    fun onHotReloaded(extras: Bundle?) {}

    /**
     * Listens for native Xposed loading events.
     *
     * Implement native Xposed compatibility here when required by a Hook.
     *
     * Use [YukiXposedEvent] to register event callbacks.
     * Use [YukiXposedEvent.xposedInterface] for native libxposed operations.
     *
     * Available events:
     *
     * [YukiXposedEvent.onModuleLoaded]
     *
     * [YukiXposedEvent.onPackageLoaded]
     *
     * [YukiXposedEvent.onPackageReady]
     *
     * [YukiXposedEvent.onSystemServerStarting]
     *
     * - Use this callback only for native Xposed APIs. Do not operate [YukiHookAPI] here.
     */
    fun onXposedEvent() {}
}