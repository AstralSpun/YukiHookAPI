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
package com.highcapable.yukihookapi.hook.dexkit.bean

import com.highcapable.yukihookapi.hook.dexkit.FieldFinder
import com.highcapable.yukihookapi.hook.dexkit.MethodFinder
import com.highcapable.yukihookapi.hook.dexkit.internal.DexResolverRuntime
import org.luckypray.dexkit.query.enums.MatchType

/** Field query properties used by `DexResolver.findField`. */
class FieldInfo {

    var declaredClass: Class<*>? = null
    var fieldName: String? = null
    var fieldType: Class<*>? = null
    var modifiers = -1
    var matchType = MatchType.Contains
    var searchPackages: Array<String>? = null
    var excludePackages: Array<String>? = null
    var readMethods: Array<MethodFinder>? = null
    var writeMethods: Array<MethodFinder>? = null

    /** Creates a standalone finder from these properties. */
    fun generate() = configure(FieldFinder())

    @JvmSynthetic
    internal fun generate(runtime: DexResolverRuntime) = configure(FieldFinder.create(runtime))

    private fun configure(finder: FieldFinder) = finder.apply {
        this@FieldInfo.declaredClass?.let(::declaredClass)
        this@FieldInfo.fieldName?.let(::fieldName)
        this@FieldInfo.fieldType?.let(::fieldType)
        if (this@FieldInfo.modifiers != -1) modifiers(this@FieldInfo.modifiers, this@FieldInfo.matchType)
        this@FieldInfo.searchPackages?.let { searchPackages(*it) }
        this@FieldInfo.excludePackages?.let { excludePackages(*it) }
        this@FieldInfo.readMethods?.let { readMethods(*it) }
        this@FieldInfo.writeMethods?.let { writeMethods(*it) }
    }
}