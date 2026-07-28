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
 * This file is created by fankes on 2022/4/29.
 */
@file:Suppress("unused", "DEPRECATION", "DiscouragedApi", "NON_PUBLIC_CALL_FROM_PUBLIC_INLINE", "UNUSED_PARAMETER")

package com.highcapable.yukihookapi.hook.xposed.bridge.resources

import android.content.res.Resources
import android.view.View
import com.highcapable.yukihookapi.hook.log.YLog

/**
 * Adapter layer for host [Resources].
 *
 * Legacy Xposed resource replacement and layout callbacks are not available in libxposed API 102.
 * Module resource loading remains available through `injectModuleAppResources`.
 * @param baseInstance the original instance.
 */
class YukiResources private constructor(private val baseInstance: Resources) :
    Resources(
        runCatching { baseInstance.assets }.getOrNull(),
        runCatching { baseInstance.displayMetrics }.getOrNull(),
        runCatching { baseInstance.configuration }.getOrNull()
    ) {

        internal companion object {

            /** Creates a [YukiResources] instance from [Resources]. */
            internal fun wrapper(baseInstance: Resources) = YukiResources(baseInstance)

            /** Reports an operation removed from the modern Xposed API. */
            private fun unsupported(name: String) =
                YLog.innerE("Resources Hook operation \"$name\" is not supported by libxposed API 102")

            /** Legacy system-wide replacement compatibility entry. */
            internal fun setSystemWideReplacement(resId: Int, replacement: Any?, callback: () -> Unit = {}) =
                unsupported("setSystemWideReplacement")

            /** Legacy system-wide replacement compatibility entry. */
            internal fun setSystemWideReplacement(
                packageName: String,
                type: String,
                name: String,
                replacement: Any?,
                callback: () -> Unit = {}
            ) = unsupported("setSystemWideReplacement")

            /** Legacy system-wide layout callback compatibility entry. */
            internal fun hookSystemWideLayout(resId: Int, initiate: LayoutInflatedParam.() -> Unit, callback: () -> Unit = {}) =
                unsupported("hookSystemWideLayout")

            /** Legacy system-wide layout callback compatibility entry. */
            internal fun hookSystemWideLayout(
                packageName: String,
                type: String,
                name: String,
                initiate: LayoutInflatedParam.() -> Unit,
                callback: () -> Unit = {}
            ) = unsupported("hookSystemWideLayout")
        }

        /** Legacy resource replacement compatibility entry. */
        internal fun setReplacement(resId: Int, replacement: Any?, callback: () -> Unit = {}) = unsupported("setReplacement")

        /** Legacy resource replacement compatibility entry. */
        internal fun setReplacement(
            packageName: String,
            type: String,
            name: String,
            replacement: Any?,
            callback: () -> Unit = {}
        ) = unsupported("setReplacement")

        /** Legacy layout callback compatibility entry. */
        internal fun hookLayout(resId: Int, initiate: LayoutInflatedParam.() -> Unit, callback: () -> Unit = {}) = unsupported("hookLayout")

        /** Legacy layout callback compatibility entry. */
        internal fun hookLayout(
            packageName: String,
            type: String,
            name: String,
            initiate: LayoutInflatedParam.() -> Unit,
            callback: () -> Unit = {}
        ) = unsupported("hookLayout")

        /**
         * Target layout resource implementation for the host app.
         * @param variantName the resource directory qualifier.
         * @param currentView the inflated layout root.
         * @param resources the host resources.
         * @param packageName the host package name.
         */
        class LayoutInflatedParam internal constructor(
            val variantName: String,
            val currentView: View,
            private val resources: Resources,
            private val packageName: String
        ) {

            /** Finds a [View] with the specified ID in the host app by identifier. */
            inline fun <reified T : View> View.findViewByIdentifier(name: String): T? =
                findViewById(resources.getIdentifier(name, "id", packageName))

            /** Finds a [View] with the specified ID in the currently loaded layout by identifier. */
            inline fun <reified T : View> findViewByIdentifier(name: String) = currentView.findViewByIdentifier<T>(name)

            override fun toString() = "LayoutInflatedParam(variantName=$variantName, currentView=$currentView)"
        }

        override fun toString() = "YukiResources by $baseInstance"
    }