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
import com.highcapable.yukihookapi.hook.param.PackageParam
import org.luckypray.dexkit.query.enums.MatchType
import java.lang.reflect.Method

/** Method query properties used by `DexResolver.findMethod`. */
class MethodInfo {

    var declaredClass: Class<*>? = null

    /** Parameter types. A null element matches any type at that position. */
    var parameters: Array<Class<*>?>? = null
    var methodName: String? = null
    var returnType: Class<*>? = null
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

    /** Creates a standalone finder from these properties. */
    fun generate() = configure(MethodFinder())

    @JvmSynthetic
    internal fun generate(packageParam: PackageParam, runtime: DexResolverRuntime) =
        configure(MethodFinder.create(packageParam, runtime))

    private fun configure(finder: MethodFinder) = finder.apply {
        this@MethodInfo.declaredClass?.let(::declaredClass)
        this@MethodInfo.parameters?.let { parameters(*it) }
        this@MethodInfo.methodName?.let(::methodName)
        this@MethodInfo.returnType?.let(::returnType)
        this@MethodInfo.usedString?.let { usedString(*it) }
        this@MethodInfo.invokeMethods?.let { invokeMethods(*it) }
        this@MethodInfo.callMethods?.let { callMethods(*it) }
        this@MethodInfo.usingNumbers?.let { usingNumbers(*it) }
        if (this@MethodInfo.paramCount != -1) paramCount(this@MethodInfo.paramCount)
        if (this@MethodInfo.modifiers != -1) modifiers(this@MethodInfo.modifiers, this@MethodInfo.matchType)
        this@MethodInfo.searchPackages?.let { searchPackages(*it) }
        this@MethodInfo.excludePackages?.let { excludePackages(*it) }
        this@MethodInfo.usedFields?.let { usedFields(*it) }
    }
}