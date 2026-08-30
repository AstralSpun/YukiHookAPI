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

/** DexKit constructor finder. */
class ConstructorFinder private constructor(
    private val packageParam: PackageParam? = null,
    runtime: DexResolverRuntime? = null
) {

    @Volatile
    private var runtime = runtime

    /** Creates a standalone constructor finder. */
    constructor() : this(null, DexResolverRuntime.currentOrNull())

    private var declaredClass: Class<*>? = null
    private val parameters = mutableListOf<Class<*>>()
    private val usedString = mutableListOf<String>()
    private val invokeMethods = mutableListOf<Method>()
    private val callMethods = mutableListOf<Method>()
    private val usingNumbers = mutableListOf<Long>()
    private val notMatchers = mutableListOf<MethodFinder>()
    private var paramCount = -1
    private var modifiers = -1
    private var matchType = MatchType.Contains
    private val searchPackages = mutableListOf<String>()
    private val excludePackages = mutableListOf<String>()
    private val usedFields = mutableListOf<FieldFinder>()
    private var searchClassFinder: ClassFinder? = null

    fun usedFields(vararg fieldFinders: FieldFinder) = apply { usedFields += fieldFinders }

    fun usedFields(vararg fields: Field) = apply { usedFields += fields.map(FieldFinder::from) }

    fun declaredClass(declaredClass: Class<*>?) = apply { this.declaredClass = declaredClass }

    fun parameters(vararg parameters: Class<*>) = apply { this.parameters += parameters }

    fun invokeMethods(vararg methods: Method) = apply { invokeMethods += methods }

    fun callMethods(vararg methods: Method) = apply { callMethods += methods }

    fun usingNumbers(vararg numbers: Long) = apply { usingNumbers += numbers.toList() }

    @JvmSynthetic
    internal fun not(matcher: MethodFinder) = apply { notMatchers += matcher }

    fun paramCount(count: Int) = apply { paramCount = count }

    fun usedString(vararg strings: String) = apply { usedString += strings }

    fun modifiers(modifiers: Int, matchType: MatchType) = apply {
        this.modifiers = modifiers
        this.matchType = matchType
    }

    fun searchPackages(vararg packages: String) = apply { searchPackages += packages }

    fun excludePackages(vararg packages: String) = apply { excludePackages += packages }

    @JvmSynthetic
    internal fun searchInClass(classFinder: ClassFinder) = apply { searchClassFinder = classFinder }

    /** Finds all matching constructors. */
    fun find(): List<Constructor<*>> {
        val runtime = requireRuntime()
        val query = buildFindMethod()
        val key = queryHashKey(query)
        runtime.cache.getConstructorList(key)?.let { return it }
        return try {
            runtime.withBridge { bridge ->
                val result = searchClassFinder?.findData(bridge)?.findMethod(query) ?: bridge.findMethod(query)
                result.map {
                    it.getConstructorInstance(runtime.classLoader).apply { isAccessible = true }
                }
            }.also { runtime.cache.putConstructorList(key, it) }
        } catch (_: NoSuchMethodException) {
            emptyList()
        }
    }

    /** Returns the first matching constructor, or null when no constructor matches. */
    fun firstOrNull(): Constructor<*>? = find().firstOrNull()

    /** Returns the first matching constructor. */
    @Throws(NoSuchMethodException::class)
    fun first(): Constructor<*> = firstOrNull() ?: throw NoSuchMethodException("No constructor found: $this")

    /** Returns whether this finder has a cached result. */
    fun existCache(): Boolean {
        val runtime = requireRuntime()
        return runtime.cache.containsConstructorList(queryHashKey(buildFindMethod()))
    }

    /** Hooks the first matching constructor with the current [PackageParam]. */
    fun hook(priority: YukiHookPriority = YukiHookPriority.DEFAULT): YukiMemberHookCreator.MemberHookCreator {
        val packageParam = requirePackageParam()
        return with(packageParam) { this@ConstructorFinder.first().hook(priority) }
    }

    /** Hooks the first matching constructor without requiring an intermediate [first] call. */
    fun hook(
        priority: YukiHookPriority = YukiHookPriority.DEFAULT,
        initiate: YukiMemberHookCreator.MemberHookCreator.() -> Unit
    ): YukiMemberHookCreator.MemberHookCreator.Result {
        val packageParam = requirePackageParam()
        return with(packageParam) { this@ConstructorFinder.first().hook(priority, initiate) }
    }

    private fun buildFindMethod() = FindMethod.create().apply {
        if (this@ConstructorFinder.searchPackages.isNotEmpty()) searchPackages(this@ConstructorFinder.searchPackages)
        if (this@ConstructorFinder.excludePackages.isNotEmpty()) excludePackages(this@ConstructorFinder.excludePackages)
        matcher(MethodMatcher.create().apply {
            name(CONSTRUCTOR_NAME)
            this@ConstructorFinder.declaredClass?.let(::declaredClass)
            this@ConstructorFinder.parameters.forEach(::addParamType)
            if (this@ConstructorFinder.usedString.isNotEmpty()) usingStrings(*this@ConstructorFinder.usedString.toTypedArray())
            this@ConstructorFinder.usedFields.forEach { addUsingField(it.buildFieldMatcher()) }
            this@ConstructorFinder.invokeMethods.forEach { addInvoke(MethodMatcher.create(it)) }
            this@ConstructorFinder.callMethods.forEach { addCaller(MethodMatcher.create(it)) }
            this@ConstructorFinder.usingNumbers.forEach(::addUsingNumber)
            this@ConstructorFinder.notMatchers.forEach { addNoneOf(it.buildMethodMatcher()) }
            if (this@ConstructorFinder.paramCount != -1) paramCount(this@ConstructorFinder.paramCount)
            if (this@ConstructorFinder.modifiers != -1) modifiers(this@ConstructorFinder.modifiers, this@ConstructorFinder.matchType)
        })
    }

    private fun queryHashKey(query: FindMethod) = searchClassFinder?.let {
        "class:${it.queryHashKey()}:constructor:${query.hashKey()}"
    } ?: query.hashKey()

    private fun requireRuntime() = runtime ?: synchronized(this) {
        runtime ?: DexResolverRuntime.current().also { runtime = it }
    }

    private fun requirePackageParam() = packageParam ?: requireRuntime().packageParam

    override fun toString() = buildString {
        append("ctor")
        declaredClass?.name?.let(::append)
        if (parameters.isNotEmpty()) append(parameters)
        if (invokeMethods.isNotEmpty()) append(invokeMethods)
        if (callMethods.isNotEmpty()) append(callMethods)
        if (usedFields.isNotEmpty()) append(usedFields)
        if (usingNumbers.isNotEmpty()) append(usingNumbers)
        if (notMatchers.isNotEmpty()) append(notMatchers)
        if (paramCount != -1) append(paramCount)
        if (modifiers != -1) append(modifiers)
        if (usedString.isNotEmpty()) append(usedString)
        if (searchPackages.isNotEmpty()) append(searchPackages)
        if (excludePackages.isNotEmpty()) append(excludePackages)
    }

    companion object {

        private const val CONSTRUCTOR_NAME = "<init>"

        @JvmSynthetic
        internal fun create(packageParam: PackageParam, runtime: DexResolverRuntime) = ConstructorFinder(packageParam, runtime)
    }
}