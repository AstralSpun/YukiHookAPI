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

import com.highcapable.yukihookapi.hook.dexkit.internal.DexResolverRuntime
import org.luckypray.dexkit.query.FindField
import org.luckypray.dexkit.query.enums.MatchType
import org.luckypray.dexkit.query.matchers.FieldMatcher
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** XPHelper-compatible DexKit field finder. */
class FieldFinder private constructor(runtime: DexResolverRuntime? = null) {

    @Volatile
    private var runtime = runtime

    /** Creates a finder that can be used as a nested matcher. */
    constructor() : this(DexResolverRuntime.currentOrNull())

    private var declaredClass: Class<*>? = null
    private var fieldName: String? = null
    private var fieldType: Class<*>? = null
    private var modifiers = -1
    private var matchType = MatchType.Contains
    private val searchPackages = mutableListOf<String>()
    private val excludePackages = mutableListOf<String>()
    private val readMethods = mutableListOf<MethodFinder>()
    private val writeMethods = mutableListOf<MethodFinder>()

    fun readMethods(vararg methods: MethodFinder) = apply { readMethods += methods }

    fun readMethods(vararg methods: Method) = apply { readMethods += methods.map(MethodFinder::from) }

    fun writeMethods(vararg methods: MethodFinder) = apply { writeMethods += methods }

    fun writeMethods(vararg methods: Method) = apply { writeMethods += methods.map(MethodFinder::from) }

    fun declaredClass(declaredClass: Class<*>?) = apply { this.declaredClass = declaredClass }

    fun fieldName(name: String?) = apply { fieldName = name }

    fun fieldType(fieldType: Class<*>?) = apply { this.fieldType = fieldType }

    fun modifiers(modifiers: Int, matchType: MatchType) = apply {
        this.modifiers = modifiers
        this.matchType = matchType
    }

    fun searchPackages(vararg packages: String) = apply { searchPackages += packages }

    fun excludePackages(vararg packages: String) = apply { excludePackages += packages }

    /** Builds the DexKit matcher represented by this finder. */
    fun buildFieldMatcher(): FieldMatcher = FieldMatcher.create().apply {
        this@FieldFinder.declaredClass?.let(::declaredClass)
        this@FieldFinder.fieldName?.let(::name)
        this@FieldFinder.fieldType?.let(::type)
        if (this@FieldFinder.modifiers != -1) modifiers(this@FieldFinder.modifiers, this@FieldFinder.matchType)
        this@FieldFinder.readMethods.forEach { addReadMethod(it.buildMethodMatcher()) }
        this@FieldFinder.writeMethods.forEach { addWriteMethod(it.buildMethodMatcher()) }
    }

    /** Finds all matching fields. */
    fun find(): List<Field> {
        val runtime = requireRuntime()
        val query = buildFindField()
        runtime.cache.getFieldList(query.hashKey())?.let { return it }
        return try {
            runtime.withBridge { bridge ->
                bridge.findField(query).map {
                    it.getFieldInstance(runtime.classLoader).apply { isAccessible = true }
                }
            }.also { runtime.cache.putFieldList(query.hashKey(), it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Returns the first matching field, or null when no field matches. */
    fun firstOrNull(): Field? = find().firstOrNull()

    /** Returns the first matching field. */
    @Throws(NoSuchFieldException::class)
    fun first(): Field = firstOrNull() ?: throw NoSuchFieldException("Field not found: $this")

    /** Returns whether this finder has a cached result. */
    fun existCache(): Boolean {
        val runtime = requireRuntime()
        return runtime.cache.containsFieldList(buildFindField().hashKey())
    }

    private fun buildFindField() = FindField.create().apply {
        if (this@FieldFinder.searchPackages.isNotEmpty()) searchPackages(this@FieldFinder.searchPackages)
        if (this@FieldFinder.excludePackages.isNotEmpty()) excludePackages(this@FieldFinder.excludePackages)
        matcher(buildFieldMatcher())
    }

    private fun requireRuntime() = runtime ?: synchronized(this) {
        runtime ?: DexResolverRuntime.current().also { runtime = it }
    }

    override fun toString() = buildString {
        append("ff")
        declaredClass?.name?.let(::append)
        fieldName?.let(::append)
        fieldType?.name?.let(::append)
        if (modifiers != -1) append(Modifier.toString(modifiers))
        if (searchPackages.isNotEmpty()) append(searchPackages)
        if (excludePackages.isNotEmpty()) append(excludePackages)
        if (readMethods.isNotEmpty()) append(readMethods)
        if (writeMethods.isNotEmpty()) append(writeMethods)
    }

    companion object {

        @JvmSynthetic
        internal fun create(runtime: DexResolverRuntime) = FieldFinder(runtime)

        /** Creates a finder that can be used as a nested matcher. */
        @JvmStatic
        fun build() = FieldFinder()

        /** Converts a reflected field to a DexKit matcher. */
        @JvmStatic
        fun toFieldMatcher(field: Field) = FieldMatcher.create(field)

        /** Creates a finder matching the reflected field exactly. */
        @JvmStatic
        fun from(field: Field) = FieldFinder().apply {
            declaredClass = field.declaringClass
            fieldName = field.name
            fieldType = field.type
            modifiers = field.modifiers
            matchType = MatchType.Equals
        }
    }
}