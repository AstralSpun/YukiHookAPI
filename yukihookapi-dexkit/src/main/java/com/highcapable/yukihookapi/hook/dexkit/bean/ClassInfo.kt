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

import com.highcapable.yukihookapi.hook.dexkit.ClassFinder
import com.highcapable.yukihookapi.hook.dexkit.FieldFinder
import com.highcapable.yukihookapi.hook.dexkit.MethodFinder
import com.highcapable.yukihookapi.hook.dexkit.internal.DexResolverRuntime
import org.luckypray.dexkit.query.enums.MatchType

/** Class query properties used by `DexResolver.findClass`. */
class ClassInfo {

    var className: String? = null
    var superClass: String? = null
    var interfaces: Array<String>? = null
    var modifiers = -1
    var matchType = MatchType.Contains
    var searchPackages: Array<String>? = null
    var excludePackages: Array<String>? = null
    var fields: Array<FieldFinder>? = null
    var methods: Array<MethodFinder>? = null
    var usedString: Array<String>? = null

    /** Creates a standalone finder from these properties. */
    fun generate() = configure(ClassFinder())

    @JvmSynthetic
    internal fun generate(runtime: DexResolverRuntime) = configure(ClassFinder.create(runtime))

    private fun configure(finder: ClassFinder) = finder.apply {
        this@ClassInfo.className?.let(::className)
        this@ClassInfo.superClass?.let(::superClass)
        this@ClassInfo.interfaces?.let { addInterface(*it) }
        if (this@ClassInfo.modifiers != -1) modifiers(this@ClassInfo.modifiers, this@ClassInfo.matchType)
        this@ClassInfo.searchPackages?.let { searchPackages(*it) }
        this@ClassInfo.excludePackages?.let { excludePackages(*it) }
        this@ClassInfo.fields?.let { fields(*it) }
        this@ClassInfo.methods?.let { methods(*it) }
        this@ClassInfo.usedString?.let { usedString(*it) }
    }
}