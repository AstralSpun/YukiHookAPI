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

import com.highcapable.yukihookapi.hook.dexkit.bean.ConstructorInfo
import com.highcapable.yukihookapi.hook.dexkit.bean.FieldInfo
import com.highcapable.yukihookapi.hook.dexkit.bean.MethodInfo
import com.highcapable.yukihookapi.hook.dexkit.internal.DexResolverRuntime
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.enums.MatchType
import org.luckypray.dexkit.query.matchers.ClassMatcher
import java.lang.reflect.Modifier

/** XPHelper-compatible DexKit class finder. */
class ClassFinder private constructor(runtime: DexResolverRuntime? = null) {

    @Volatile
    private var runtime = runtime

    /** Creates a finder that can be used as a nested matcher. */
    constructor() : this(DexResolverRuntime.currentOrNull())

    private val interfaces = mutableListOf<String>()
    private val searchPackages = mutableListOf<String>()
    private val excludePackages = mutableListOf<String>()
    private val fields = mutableListOf<FieldFinder>()
    private val methods = mutableListOf<MethodFinder>()
    private val usedString = mutableListOf<String>()
    private var className: String? = null
    private var superClass: String? = null
    private var modifiers = -1
    private var matchType = MatchType.Contains

    fun usedString(vararg strings: String) = apply { usedString += strings }

    fun className(name: String?) = apply { className = name }

    fun superClass(superClass: String?) = apply { this.superClass = superClass }

    fun addInterface(vararg interfaces: String) = apply { this.interfaces += interfaces }

    fun modifiers(modifiers: Int, matchType: MatchType) = apply {
        this.modifiers = modifiers
        this.matchType = matchType
    }

    fun searchPackages(vararg packages: String) = apply { searchPackages += packages }

    fun excludePackages(vararg packages: String) = apply { excludePackages += packages }

    fun fields(vararg fields: FieldFinder) = apply { this.fields += fields }

    fun methods(vararg methods: MethodFinder) = apply { this.methods += methods }

    /** Creates a method finder restricted to the classes matched by this finder. */
    @JvmSynthetic
    fun findMethod(methodInfo: MethodInfo.() -> Unit): MethodFinder {
        val runtime = requireRuntime()
        return MethodInfo().apply(methodInfo).generate(runtime.packageParam, runtime).searchInClass(this)
    }

    /** Creates a constructor finder restricted to the classes matched by this finder. */
    @JvmSynthetic
    fun findConstructor(constructorInfo: ConstructorInfo.() -> Unit): ConstructorFinder {
        val runtime = requireRuntime()
        return ConstructorInfo().apply(constructorInfo).generate(runtime.packageParam, runtime).searchInClass(this)
    }

    /** Creates a field finder restricted to the classes matched by this finder. */
    @JvmSynthetic
    fun findField(fieldInfo: FieldInfo.() -> Unit): FieldFinder {
        val runtime = requireRuntime()
        return FieldInfo().apply(fieldInfo).generate(runtime).searchInClass(this)
    }

    /** Builds the DexKit matcher represented by this finder. */
    fun buildClassMatcher(): ClassMatcher = ClassMatcher.create().apply {
        this@ClassFinder.className?.let(::className)
        this@ClassFinder.superClass?.let(::superClass)
        this@ClassFinder.interfaces.forEach(::addInterface)
        if (this@ClassFinder.usedString.isNotEmpty()) usingStrings(this@ClassFinder.usedString)
        if (this@ClassFinder.modifiers != -1) modifiers(this@ClassFinder.modifiers, this@ClassFinder.matchType)
        this@ClassFinder.fields.forEach { addField(it.buildFieldMatcher()) }
        this@ClassFinder.methods.forEach { addMethod(it.buildMethodMatcher()) }
    }

    /** Finds all matching classes. */
    fun find(): List<Class<*>> {
        val runtime = requireRuntime()
        val query = buildFindClass()
        runtime.cache.getClassList(query.hashKey())?.let { return it }
        return try {
            runtime.withBridge { bridge ->
                bridge.findClass(query).map { it.getInstance(runtime.classLoader) }
            }.also { runtime.cache.putClassList(query.hashKey(), it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Returns the first matching class, or null when no class matches. */
    fun firstOrNull(): Class<*>? = find().firstOrNull()

    /** Returns the first matching class. */
    @Throws(ClassNotFoundException::class)
    fun first(): Class<*> = firstOrNull() ?: throw ClassNotFoundException("Class not found: $this")

    /** Returns whether this finder has a cached result. */
    fun existCache(): Boolean {
        val runtime = requireRuntime()
        return runtime.cache.containsClassList(buildFindClass().hashKey())
    }

    private fun buildFindClass() = FindClass.create().apply {
        if (this@ClassFinder.searchPackages.isNotEmpty()) searchPackages(this@ClassFinder.searchPackages)
        if (this@ClassFinder.excludePackages.isNotEmpty()) excludePackages(this@ClassFinder.excludePackages)
        matcher(buildClassMatcher())
    }

    @JvmSynthetic
    internal fun findData(bridge: DexKitBridge) = bridge.findClass(buildFindClass())

    @JvmSynthetic
    internal fun queryHashKey() = buildFindClass().hashKey()

    private fun requireRuntime() = runtime ?: synchronized(this) {
        runtime ?: DexResolverRuntime.current().also { runtime = it }
    }

    override fun toString() = buildString {
        append("cf")
        className?.let(::append)
        superClass?.let(::append)
        if (usedString.isNotEmpty()) append(usedString)
        if (interfaces.isNotEmpty()) append(interfaces)
        if (modifiers != -1) append(Modifier.toString(modifiers))
        if (searchPackages.isNotEmpty()) append(searchPackages)
        if (excludePackages.isNotEmpty()) append(excludePackages)
        if (fields.isNotEmpty()) append(fields)
        if (methods.isNotEmpty()) append(methods)
    }

    companion object {

        @JvmSynthetic
        internal fun create(runtime: DexResolverRuntime) = ClassFinder(runtime)

        /** Creates a finder that can be used as a nested matcher. */
        @JvmStatic
        fun build() = ClassFinder()

        /** Creates a finder matching the reflected class exactly. */
        @JvmStatic
        fun from(clazz: Class<*>) = ClassFinder().apply {
            className = clazz.name
            superClass = clazz.superclass?.name
            interfaces += clazz.interfaces.map { it.name }
            modifiers = clazz.modifiers
            matchType = MatchType.Equals
        }
    }
}