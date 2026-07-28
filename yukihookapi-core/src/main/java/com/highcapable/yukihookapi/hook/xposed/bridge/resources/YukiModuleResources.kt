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
@file:Suppress("DEPRECATION", "DiscouragedPrivateApi", "PrivateApi")

package com.highcapable.yukihookapi.hook.xposed.bridge.resources

import android.content.res.Resources
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.loader.ResourcesLoader
import android.content.res.loader.ResourcesProvider
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.DisplayMetrics
import androidx.annotation.RequiresApi
import java.io.File
import java.io.IOException

/**
 * Wraps the current module's standalone [Resources] instance.
 * @param baseInstance the original instance.
 */
class YukiModuleResources private constructor(private val baseInstance: Resources, private val modulePath: String) :
    Resources(
        runCatching { baseInstance.assets }.getOrNull(),
        runCatching { baseInstance.displayMetrics }.getOrNull(),
        runCatching { baseInstance.configuration }.getOrNull()
    ) {

        internal companion object {

            /**
             * Creates a module-scoped [Resources] instance.
             * @param path the Xposed module APK path.
             * @return [YukiModuleResources]
             */
            internal fun wrapper(path: String) = YukiModuleResources(ModuleResourcesFactory.create(path), path)
        }

        /**
         * Creates a resource forwarder containing this resource instance and ID.
         * @param resId resources Id.
         * @return [YukiResForwarder]
         */
        fun fwd(resId: Int) = YukiResForwarder.wrapper(this, resId)

        /** Injects this module's resource loader into target resources. */
        internal fun injectTo(resources: Resources) = ModuleResourcesFactory.inject(resources, modulePath)

        override fun toString() = "YukiModuleResources by $baseInstance"
    }

/** Creates and injects module resources without relying on the legacy Xposed resources API. */
private object ModuleResourcesFactory {

    /** Creates an isolated resource instance for the module APK. */
    fun create(modulePath: String): Resources {
        require(modulePath.isNotBlank()) { "Module APK path must not be blank" }
        val systemResources = Resources.getSystem()
        val metrics = DisplayMetrics().also { it.setTo(systemResources.displayMetrics) }
        val configuration = Configuration(systemResources.configuration)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            runCatching { ResourcesLoaderImpl.create(modulePath, metrics, configuration) }
                .getOrElse { LegacyImpl.create(modulePath, metrics, configuration) }
        else LegacyImpl.create(modulePath, metrics, configuration)
    }

    /** Adds the module APK resources to an existing resource instance. */
    fun inject(resources: Resources, modulePath: String) {
        require(modulePath.isNotBlank()) { "Module APK path must not be blank" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            runCatching { ResourcesLoaderImpl.inject(resources, modulePath) }
                .getOrElse { LegacyImpl.inject(resources.assets, modulePath) }
        else LegacyImpl.inject(resources.assets, modulePath)
    }

    /** Legacy AssetManager implementation used below Android 11 and as a compatibility fallback. */
    private object LegacyImpl {

        private val assetManagerConstructor by lazy {
            AssetManager::class.java.getDeclaredConstructor().apply { isAccessible = true }
        }

        private val addAssetPathMethod by lazy {
            AssetManager::class.java.getDeclaredMethod("addAssetPath", String::class.java).apply { isAccessible = true }
        }

        fun create(modulePath: String, metrics: DisplayMetrics, configuration: Configuration): Resources {
            val assetManager = assetManagerConstructor.newInstance()
            inject(assetManager, modulePath)
            return Resources(assetManager, metrics, configuration)
        }

        fun inject(assetManager: AssetManager, modulePath: String) {
            val cookie = addAssetPathMethod.invoke(assetManager, modulePath) as? Int ?: 0
            require(cookie != 0) { "AssetManager.addAssetPath($modulePath) failed" }
        }
    }

    /** Android 11+ ResourcesLoader implementation. */
    @RequiresApi(Build.VERSION_CODES.R)
    private object ResourcesLoaderImpl {

        private val loaders = mutableMapOf<String, ResourcesLoader>()

        private val builderConstructor by lazy {
            Class.forName("android.content.res.AssetManager\$Builder")
                .getDeclaredConstructor().apply { isAccessible = true }
        }

        private val builderAddLoaderMethod by lazy {
            builderConstructor.declaringClass.getDeclaredMethod("addLoader", ResourcesLoader::class.java).apply { isAccessible = true }
        }

        private val builderBuildMethod by lazy {
            builderConstructor.declaringClass.getDeclaredMethod("build").apply { isAccessible = true }
        }

        fun create(modulePath: String, metrics: DisplayMetrics, configuration: Configuration): Resources {
            val builder = runCatching { builderConstructor.newInstance() }
                .getOrElse { throw IllegalStateException("Cannot instantiate AssetManager.Builder", it) }
            return runCatching {
                builderAddLoaderMethod.invoke(builder, loader(modulePath))
                val assetManager = builderBuildMethod.invoke(builder) as? AssetManager
                    ?: error("AssetManager.Builder.build() returned null")
                Resources(assetManager, metrics, configuration)
            }.getOrElse { throw IllegalStateException("Failed to create module Resources", it) }
        }

        fun inject(resources: Resources, modulePath: String) {
            resources.addLoaders(loader(modulePath))
        }

        @Synchronized
        private fun loader(modulePath: String) = loaders[modulePath] ?: ResourcesLoader().apply {
            addProvider(createProvider(modulePath))
        }.also { loaders[modulePath] = it }

        private fun createProvider(modulePath: String): ResourcesProvider {
            val moduleFile = File(modulePath)
            require(moduleFile.exists()) { "Module APK does not exist: $modulePath" }
            return try {
                ParcelFileDescriptor.open(moduleFile, ParcelFileDescriptor.MODE_READ_ONLY).use {
                    ResourcesProvider.loadFromApk(it)
                }
            } catch (e: IOException) {
                throw IllegalStateException("Failed to load module resources from $modulePath", e)
            }
        }
    }
}