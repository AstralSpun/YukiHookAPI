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
package com.highcapable.yukihookapi.hook.dexkit

import com.highcapable.yukihookapi.hook.core.YukiMemberHookCreator
import com.highcapable.yukihookapi.hook.core.api.priority.YukiHookPriority
import com.highcapable.yukihookapi.hook.dexkit.internal.DexResolverRuntime
import com.highcapable.yukihookapi.hook.param.PackageParam
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.enums.MatchType
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

/** XPHelper-compatible DexKit method finder. */
class MethodFinder private constructor(
    private val packageParam: PackageParam? = null,
    runtime: DexResolverRuntime? = null
) {

    @Volatile
    private var runtime = runtime

    /** Creates a finder that can be used as a nested matcher. */
    constructor() : this(null, DexResolverRuntime.currentOrNull())

    private var declaredClass: Class<*>? = null
    private val parameters = mutableListOf<Class<*>>()
    private var methodName: String? = null
    private var returnType: Class<*>? = null
    private val usedString = mutableListOf<String>()
    private val invokeMethods = mutableListOf<Method>()
    private val callMethods = mutableListOf<Method>()
    private val usingNumbers = mutableListOf<Long>()
    private var paramCount = -1
    private var modifiers = -1
    private var matchType = MatchType.Contains
    private val searchPackages = mutableListOf<String>()
    private val excludePackages = mutableListOf<String>()
    private val usedFields = mutableListOf<FieldFinder>()

    fun usedFields(vararg fieldFinders: FieldFinder) = apply { usedFields += fieldFinders }

    fun usedFields(vararg fields: Field) = apply { usedFields += fields.map(FieldFinder::from) }

    fun declaredClass(declaredClass: Class<*>?) = apply { this.declaredClass = declaredClass }

    fun parameters(vararg parameters: Class<*>) = apply { this.parameters += parameters }

    fun methodName(name: String?) = apply { methodName = name }

    fun returnType(returnTypeClass: Class<*>?) = apply { returnType = returnTypeClass }

    fun invokeMethods(vararg methods: Method) = apply { invokeMethods += methods }

    fun callMethods(vararg methods: Method) = apply { callMethods += methods }

    fun usingNumbers(vararg numbers: Long) = apply { usingNumbers += numbers.toList() }

    fun paramCount(count: Int) = apply { paramCount = count }

    fun usedString(vararg strings: String) = apply { usedString += strings }

    fun modifiers(modifiers: Int, matchType: MatchType) = apply {
        this.modifiers = modifiers
        this.matchType = matchType
    }

    fun searchPackages(vararg packages: String) = apply { searchPackages += packages }

    fun excludePackages(vararg packages: String) = apply { excludePackages += packages }

    /** Builds the DexKit matcher represented by this finder. */
    fun buildMethodMatcher(): MethodMatcher = MethodMatcher.create().apply {
        this@MethodFinder.declaredClass?.let(::declaredClass)
        this@MethodFinder.methodName?.takeIf { it.isNotEmpty() }?.let(::name)
        this@MethodFinder.returnType?.let(::returnType)
        if (this@MethodFinder.usedString.isNotEmpty()) usingStrings(*this@MethodFinder.usedString.toTypedArray())
        this@MethodFinder.parameters.forEach(::addParamType)
        this@MethodFinder.usedFields.forEach { addUsingField(it.buildFieldMatcher()) }
        this@MethodFinder.invokeMethods.forEach { addInvoke(MethodMatcher.create(it)) }
        this@MethodFinder.callMethods.forEach { addCaller(MethodMatcher.create(it)) }
        this@MethodFinder.usingNumbers.forEach(::addUsingNumber)
        if (this@MethodFinder.paramCount != -1) paramCount(this@MethodFinder.paramCount)
        if (this@MethodFinder.modifiers != -1) modifiers(this@MethodFinder.modifiers, this@MethodFinder.matchType)
    }

    /** Finds all matching normal methods. */
    fun find(): List<Method> {
        val runtime = requireRuntime()
        val query = buildFindMethod()
        runtime.cache.getMethodList(query.hashKey())?.let { return it }
        return try {
            runtime.withBridge { bridge ->
                bridge.findMethod(query).filter { it.isMethod }.map {
                    it.getMethodInstance(runtime.classLoader).apply { isAccessible = true }
                }
            }.also { runtime.cache.putMethodList(query.hashKey(), it) }
        } catch (_: NoSuchMethodException) {
            emptyList()
        }
    }

    /** Returns the first matching method, or null when no method matches. */
    fun firstOrNull(): Method? = find().firstOrNull()

    /** Returns the first matching method. */
    @Throws(Exception::class)
    fun first(): Method = firstOrNull() ?: throw NoSuchMethodException("No method found: $this")

    /** Returns whether this finder has a cached method or constructor result. */
    fun existCache(): Boolean {
        val runtime = requireRuntime()
        val key = buildFindMethod().hashKey()
        return runtime.cache.containsMethodList(key) || runtime.cache.containsConstructorList(key)
    }

    /** Finds all matching constructors. */
    fun findConstructor(): List<Constructor<*>> {
        val runtime = requireRuntime()
        val query = buildFindMethod()
        runtime.cache.getConstructorList(query.hashKey())?.let { return it }
        return try {
            runtime.withBridge { bridge ->
                bridge.findMethod(query).filter { it.isConstructor }.map {
                    it.getConstructorInstance(runtime.classLoader).apply { isAccessible = true }
                }
            }.also { runtime.cache.putConstructorList(query.hashKey(), it) }
        } catch (_: NoSuchMethodException) {
            emptyList()
        }
    }

    /** Returns the first matching constructor, or null when no constructor matches. */
    fun firstConstructorOrNull(): Constructor<*>? = findConstructor().firstOrNull()

    /** Returns the first matching constructor. */
    @Throws(Exception::class)
    fun firstConstructor(): Constructor<*> =
        firstConstructorOrNull() ?: throw NoSuchMethodException("No constructor found: $this")

    /** Hooks the first matching method with the current [PackageParam]. */
    fun hook(priority: YukiHookPriority = YukiHookPriority.DEFAULT): YukiMemberHookCreator.MemberHookCreator {
        val packageParam = requirePackageParam()
        return with(packageParam) { this@MethodFinder.first().hook(priority) }
    }

    /** Hooks the first matching method without requiring an intermediate [first] call. */
    fun hook(
        priority: YukiHookPriority = YukiHookPriority.DEFAULT,
        initiate: YukiMemberHookCreator.MemberHookCreator.() -> Unit
    ): YukiMemberHookCreator.MemberHookCreator.Result {
        val packageParam = requirePackageParam()
        return with(packageParam) { this@MethodFinder.first().hook(priority, initiate) }
    }

    private fun buildFindMethod() = FindMethod.create().apply {
        if (this@MethodFinder.searchPackages.isNotEmpty()) searchPackages(this@MethodFinder.searchPackages)
        if (this@MethodFinder.excludePackages.isNotEmpty()) excludePackages(this@MethodFinder.excludePackages)
        matcher(buildMethodMatcher())
    }

    private fun requireRuntime() = runtime ?: synchronized(this) {
        runtime ?: DexResolverRuntime.current().also { runtime = it }
    }

    private fun requirePackageParam() = packageParam ?: requireRuntime().packageParam

    override fun toString() = buildString {
        append("mf")
        declaredClass?.name?.let(::append)
        methodName?.takeIf { it.isNotEmpty() }?.let(::append)
        returnType?.name?.let(::append)
        if (parameters.isNotEmpty()) append(parameters)
        if (invokeMethods.isNotEmpty()) append(invokeMethods)
        if (callMethods.isNotEmpty()) append(callMethods)
        if (usedFields.isNotEmpty()) append(usedFields)
        if (usingNumbers.isNotEmpty()) append(usingNumbers)
        if (paramCount != -1) append(paramCount)
        if (modifiers != -1) append(modifiers)
        if (usedString.isNotEmpty()) append(usedString)
        if (searchPackages.isNotEmpty()) append(searchPackages)
        if (excludePackages.isNotEmpty()) append(excludePackages)
    }

    companion object {

        @JvmSynthetic
        internal fun create(packageParam: PackageParam, runtime: DexResolverRuntime) = MethodFinder(packageParam, runtime)

        /** Creates a finder that can be used as a nested matcher. */
        @JvmStatic
        fun build() = MethodFinder()

        /** Converts a reflected method to a DexKit matcher. */
        @JvmStatic
        fun toMethodMatcher(method: Method) = MethodMatcher.create(method)

        /** Creates a finder matching the reflected method exactly. */
        @JvmStatic
        fun from(method: Method) = MethodFinder().apply {
            declaredClass = method.declaringClass
            parameters += method.parameterTypes
            methodName = method.name
            returnType = method.returnType
            modifiers = method.modifiers
            matchType = MatchType.Equals
        }
    }
}