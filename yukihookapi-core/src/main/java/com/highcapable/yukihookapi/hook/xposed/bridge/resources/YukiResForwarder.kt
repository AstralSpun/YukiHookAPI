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
@file:Suppress("unused")

package com.highcapable.yukihookapi.hook.xposed.bridge.resources

import android.content.res.Resources

/**
 * Identifies a resource value in a source [Resources] instance.
 * @param resources the source resources.
 * @param id the source resource ID.
 */
class YukiResForwarder private constructor(val resources: Resources, val id: Int) {

    internal companion object {

        /**
         * Creates a [YukiResForwarder] from a resource instance and ID.
         * @param resources the source resources.
         * @param id the source resource ID.
         * @return [YukiResForwarder]
         */
        internal fun wrapper(resources: Resources, id: Int) = YukiResForwarder(resources, id)
    }

    /**
     * Gets the wrapped forwarder instance.
     * @return [YukiResForwarder]
     */
    internal val instance get() = this

    override fun toString() = "YukiResForwarder(resources=$resources, id=$id)"
}