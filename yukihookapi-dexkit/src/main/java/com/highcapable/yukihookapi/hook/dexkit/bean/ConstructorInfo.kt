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

import com.highcapable.yukihookapi.hook.dexkit.ConstructorFinder
import com.highcapable.yukihookapi.hook.dexkit.FieldFinder
import com.highcapable.yukihookapi.hook.dexkit.internal.DexResolverRuntime
import com.highcapable.yukihookapi.hook.param.PackageParam
import org.luckypray.dexkit.query.enums.MatchType
import java.lang.reflect.Method

/** Constructor query properties used by `DexResolver.findConstructor`. */
class ConstructorInfo {

    private val notMatchers = mutableListOf<MethodInfo>()

    var declaredClass: Class<*>? = null
    var parameters: Array<Class<*>>? = null
    var usedString: Array<String>? = null
    var invokeMethods: Array<Method>? = null
    var callMethods: Array<Method>? = null
    var usingNumbers: LongArray? = null
    var paramCount = -1
    var modifiers = -1
    var matchType = MatchType.Contains
    var searchPackages: Array<String>? = null
    var excludePackages: Array<String>? = null
    var usedFields: Array<FieldFinder>? = null

    /** Adds a negated nested method matcher. */
    @JvmSynthetic
    fun not(methodInfo: MethodInfo.() -> Unit) = apply {
        notMatchers += MethodInfo().apply(methodInfo)
    }

    /** Creates a standalone finder from these properties. */
    fun generate() = configure(ConstructorFinder())

    @JvmSynthetic
    internal fun generate(packageParam: PackageParam, runtime: DexResolverRuntime) =
        configure(ConstructorFinder.create(packageParam, runtime))

    private fun configure(finder: ConstructorFinder) = finder.apply {
        this@ConstructorInfo.declaredClass?.let(::declaredClass)
        this@ConstructorInfo.parameters?.let { parameters(*it) }
        this@ConstructorInfo.usedString?.let { usedString(*it) }
        this@ConstructorInfo.invokeMethods?.let { invokeMethods(*it) }
        this@ConstructorInfo.callMethods?.let { callMethods(*it) }
        this@ConstructorInfo.usingNumbers?.let { usingNumbers(*it) }
        this@ConstructorInfo.notMatchers.forEach { not(it.generate()) }
        if (this@ConstructorInfo.paramCount != -1) paramCount(this@ConstructorInfo.paramCount)
        if (this@ConstructorInfo.modifiers != -1) modifiers(this@ConstructorInfo.modifiers, this@ConstructorInfo.matchType)
        this@ConstructorInfo.searchPackages?.let { searchPackages(*it) }
        this@ConstructorInfo.excludePackages?.let { excludePackages(*it) }
        this@ConstructorInfo.usedFields?.let { usedFields(*it) }
    }
}