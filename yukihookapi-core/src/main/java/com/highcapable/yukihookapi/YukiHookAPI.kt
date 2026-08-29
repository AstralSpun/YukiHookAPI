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
 * This file is created by fankes on 2022/2/2.
 */
@file:Suppress("unused", "MemberVisibilityCanBePrivate", "NON_PUBLIC_CALL_FROM_PUBLIC_INLINE")

package com.highcapable.yukihookapi

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.os.Bundle
import com.highcapable.yukihookapi.YukiHookAPI.Configs.debugLog
import com.highcapable.yukihookapi.YukiHookAPI.configs
import com.highcapable.yukihookapi.YukiHookAPI.encase
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.generated.YukiHookAPIProperties
import com.highcapable.yukihookapi.hook.core.api.compat.HookApiCategoryHelper
import com.highcapable.yukihookapi.hook.core.api.compat.HookApiProperty
import com.highcapable.yukihookapi.hook.core.api.compat.type.ExecutorType
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.isTaiChiModuleActive
import com.highcapable.yukihookapi.hook.factory.processName
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.param.wrapper.PackageParamWrapper
import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication
import com.highcapable.yukihookapi.hook.xposed.bridge.YukiXposedModule
import com.highcapable.yukihookapi.hook.xposed.bridge.service.YukiXposedService
import com.highcapable.yukihookapi.hook.xposed.bridge.status.YukiXposedModuleStatus
import com.highcapable.yukihookapi.hook.xposed.bridge.type.HookEntryType
import com.highcapable.yukihookapi.hook.xposed.channel.YukiHookDataChannel
import com.highcapable.yukihookapi.hook.xposed.prefs.YukiHookPrefsBridge
import java.lang.reflect.Member

/**
 * [YukiHookAPI] loading entry point.
 *
 * Supports both module loading and custom Hook loading.
 *
 * Xposed module loading automatically adapts the relevant APIs. Call [encase] directly to complete the operation.
 *
 * Call [configs] to configure [YukiHookAPI].
 */
object YukiHookAPI {

    /** Whether the welcome message has not yet been printed. */
    private var isShowSplashLogOnceTime = true

    /** Whether loading originated from a custom Hook API. */
    internal var isLoadedFromBaseContext = false

    /** The tag name. */
    const val TAG = YukiHookAPIProperties.PROJECT_NAME

    /** The current version. */
    const val VERSION = YukiHookAPIProperties.PROJECT_YUKIHOOKAPI_CORE_VERSION

    /**
     * Version name.
     *
     * - This API is deprecated and will be removed in a future version.
     *
     * - Migrate to [VERSION].
     */
    @Deprecated(message = "Version names and version codes are no longer distinguished", ReplaceWith("VERSION"))
    const val API_VERSION_NAME = VERSION

    /**
     * Version code.
     *
     * - This API is deprecated and will be removed in a future version.
     *
     * - Migrate to [VERSION].
     */
    @Deprecated(message = "Version names and version codes are no longer distinguished", ReplaceWith("VERSION"))
    const val API_VERSION_CODE = -1

    /**
     * Current [YukiHookAPI] status.
     */
    object Status {

        /**
         * Gets the project compilation timestamp in local time.
         * @return [Long]
         */
        val compiledTimestamp get() = runCatching { YukiHookAPI_Impl.compiledTimestamp }.getOrNull() ?: 0L

        /**
         * Gets whether the current environment is a (Xposed) host environment.
         * @return [Boolean]
         */
        val isXposedEnvironment get() = YukiXposedModule.isXposedEnvironment

        /**
         * Gets the current Hook Framework name.
         *
         * - This API is deprecated and will be removed in a future version.
         *
         * - Migrate to [Executor.name].
         * @return [String]
         */
        @Deprecated(
            message = "Use the new API to implement this feature",
            ReplaceWith("Executor.name", "com.highcapable.yukihookapi.YukiHookAPI.Status.Executor")
        )
        val executorName get() = Executor.name

        /**
         * Gets the current Hook Framework version.
         *
         * - This API is deprecated and will be removed in a future version.
         *
         * - Migrate to [Executor.apiLevel], [Executor.versionName], and [Executor.versionCode].
         * @return [Int]
         */
        @Deprecated(
            message = "Use the new API to implement this feature",
            ReplaceWith("Executor.apiLevel", "com.highcapable.yukihookapi.YukiHookAPI.Status.Executor")
        )
        val executorVersion get() = Executor.apiLevel

        /**
         * Checks whether the module is active in Xposed, TaiChi, or Wuji.
         *
         * - In the module environment, [Application] must extend [ModuleApplication] to use libxposed service detection.
         *
         * - When no libxposed service is available, [InjectYukiHookWithXposed.isUsingXposedModuleStatus] must be enabled for Xposed detection.
         *
         * - In a (Xposed) host environment, only the activation state excluding [isTaiChiModuleActive] is returned.
         * @return [Boolean] whether the module is active.
         */
        val isModuleActive get() = isXposedEnvironment || Service.isAvailable || YukiXposedModuleStatus.isActive || isTaiChiModuleActive

        /**
         * Checks only whether the module is active in Xposed.
         *
         * - In the module environment, [Application] must extend [ModuleApplication] to use libxposed service detection.
         *
         * - When no libxposed service is available, [InjectYukiHookWithXposed.isUsingXposedModuleStatus] must be enabled.
         *
         * - Always returns true in a (Xposed) host environment.
         * @return [Boolean] whether the module is active.
         */
        val isXposedModuleActive get() = isXposedEnvironment || Service.isAvailable || YukiXposedModuleStatus.isActive

        /**
         * Checks only whether the module is active in TaiChi or Wuji.
         *
         * - In the module environment, [Application] must extend [ModuleApplication].
         *
         * - Always returns false in a (Xposed) host environment.
         * @return [Boolean] whether the module is active.
         */
        val isTaiChiModuleActive get() = isXposedEnvironment.not() && (ModuleApplication.currentContext?.isTaiChiModuleActive ?: false)

        /**
         * Checks whether the current Hook Framework supports Resources Hook.
         *
         * - In the module environment, the legacy status fallback requires [InjectYukiHookWithXposed.isUsingXposedModuleStatus] to be enabled.
         *
         * - Legacy resource replacement and layout hooks are unavailable in libxposed API 102, so this returns false there.
         * @return [Boolean] whether Resources Hook is supported.
         */
        val isSupportResourcesHook
            get() = when {
                isXposedEnvironment -> YukiXposedModule.isSupportResourcesHook
                Service.isAvailable -> false
                else -> YukiXposedModuleStatus.isSupportResourcesHook
            }

        /**
         * Access to libxposed services connected to the module application.
         *
         * The module's [Application] must extend [ModuleApplication] before these values become available.
         *
         * [YukiHookAPI] owns libxposed's single process-wide service listener. Registering another listener directly replaces this connection.
         */
        object Service {

            /**
             * Immutable information reported by a Hook Framework service.
             * @property id the process-local identity of this service connection.
             * @property name the Hook Framework name.
             * @property apiLevel the libxposed service API level.
             * @property versionName the Hook Framework version name.
             * @property versionCode the Hook Framework version code.
             * @property properties the Hook Framework property flags.
             */
            data class Framework(
                val id: Long,
                val name: String,
                val apiLevel: Int,
                val versionName: String,
                val versionCode: Long,
                val properties: Long
            )

            /** State of a running process hooked by the module. */
            enum class RunningTargetState {
                /** The process is running the currently installed module code. */
                UP_TO_DATE,

                /** The process is still running an older module version. */
                STALE,

                /** The process is currently being hot-reloaded. */
                RELOADING,

                /** The process's last hot reload attempt failed. */
                FAILED
            }

            /**
             * Diagnostic information about a process currently hooked by the module.
             * @property framework the Hook Framework reporting this process.
             * @property uid the process UID.
             * @property pid the process ID.
             * @property processName the Android process name.
             * @property state the current module-code state.
             * @property loadedVersionCode the module version code loaded in this process.
             */
            data class RunningTarget(
                val framework: Framework,
                val uid: Int,
                val pid: Int,
                val processName: String,
                val state: RunningTargetState,
                val loadedVersionCode: Long
            )

            /**
             * Result delivered for one Hook Framework after a scope request.
             * @property framework the Hook Framework handling this request.
             * @property isApproved whether the request was approved.
             * @property approvedPackages the packages approved by the Hook Framework.
             * @property failureMessage the failure reason, or null when approved.
             */
            data class ScopeRequestResult(
                val framework: Framework,
                val isApproved: Boolean,
                val approvedPackages: List<String> = emptyList(),
                val failureMessage: String? = null
            )

            /** Result status of a libxposed API 102 hot reload request. */
            enum class HotReloadStatus {
                /** Hot reload completed successfully. */
                SUCCEEDED,

                /** The target rejected hot reload or an exception occurred. */
                FAILED,

                /** The target or Hook Framework does not support hot reload. */
                UNSUPPORTED,

                /** Another hot reload request is already running for the target. */
                IN_PROGRESS,

                /** The target process died before hot reload completed. */
                PROCESS_DIED
            }

            /**
             * Result delivered after requesting hot reload for one running target.
             * @property target the target for which hot reload was requested.
             * @property status the hot reload completion status.
             * @property message the optional diagnostic message reported by the Hook Framework.
             */
            data class HotReloadResult(
                val target: RunningTarget,
                val status: HotReloadStatus,
                val message: String? = null
            )

            /**
             * Whether at least one libxposed service is connected and responsive.
             * @return [Boolean]
             */
            val isAvailable: Boolean get() = YukiXposedService.isAvailable

            /**
             * Gets all currently responsive Hook Framework services in binding order.
             * @return [List] of [Framework].
             */
            val frameworks: List<Framework> get() = YukiXposedService.frameworks

            /**
             * Gets the module scope reported by each responsive Hook Framework service.
             * @return [Map] from [Framework] to package names.
             */
            val scopes: Map<Framework, List<String>> get() = YukiXposedService.scopes

            /**
             * Gets running hooked processes reported by service API 102 Hook Frameworks.
             * @return [List] of [RunningTarget].
             */
            val runningTargets: List<RunningTarget> get() = YukiXposedService.runningTargets

            /**
             * Requests packages to be added to the module scope in every responsive Hook Framework.
             *
             * The [callback] may run on a Binder thread and is invoked once for each dispatched request.
             * @param packages package names to request.
             * @param callback callback for each Hook Framework result.
             * @return [Boolean] whether at least one request was dispatched.
             */
            fun requestScope(packages: List<String>, callback: (ScopeRequestResult) -> Unit): Boolean =
                YukiXposedService.requestScope(packages.toList(), callback)

            /**
             * Removes packages from the module scope in every responsive Hook Framework.
             * @param packages package names to remove.
             * @return [Boolean] whether at least one removal was dispatched.
             */
            fun removeScope(packages: List<String>): Boolean = YukiXposedService.removeScope(packages.toList())

            /**
             * Requests manual hot reload for one running target.
             *
             * Manual requests remain available when [Configs.isEnableAutoHotReload] is disabled.
             * The [target] must be an instance returned by [runningTargets]; copied or manually created instances are rejected.
             * The [extras] must contain only class-loader-neutral framework values.
             *
             * The [callback] may run on a Binder thread.
             * @param target the running target to reload.
             * @param extras optional data delivered to the old and new Hook entry generations.
             * @param callback callback invoked when the request completes.
             * @return [Boolean] whether the request was dispatched.
             */
            fun hotReload(
                target: RunningTarget,
                extras: Bundle? = null,
                callback: (HotReloadResult) -> Unit = {}
            ): Boolean = YukiXposedService.hotReload(target, extras, callback)

            /**
             * Requests manual hot reload for all [RunningTargetState.STALE] and [RunningTargetState.FAILED] targets.
             *
             * [RunningTargetState.UP_TO_DATE] and [RunningTargetState.RELOADING] targets are skipped.
             * Manual requests remain available when [Configs.isEnableAutoHotReload] is disabled.
             * The [extras] must contain only class-loader-neutral framework values.
             *
             * The [callback] may run on a Binder thread and is invoked once for each dispatched target.
             * @param extras optional data delivered to the old and new Hook entry generations.
             * @param callback callback invoked for each completed request.
             * @return [Int] number of requests dispatched.
             */
            fun hotReloadAll(
                extras: Bundle? = null,
                callback: (HotReloadResult) -> Unit = {}
            ): Int = YukiXposedService.hotReloadAll(extras, callback)
        }

        /**
         * Information about the Hook Framework used by the current [YukiHookAPI].
         *
         * In the module environment, the primary libxposed service is preferred and the legacy injected status is used as a fallback.
         */
        object Executor {

            /**
             * Gets the current Hook Framework name.
             *
             * @return [String] `unknown` when unavailable or `invalid` when resolution fails.
             */
            val name
                get() = HookApiProperty.name.takeIf { isXposedEnvironment }
                    ?: YukiXposedService.primaryFramework?.name
                    ?: when {
                        isXposedModuleActive -> YukiXposedModuleStatus.executorName
                        isTaiChiModuleActive -> HookApiProperty.TAICHI_XPOSED_NAME
                        else -> YukiXposedModuleStatus.executorName
                    }

            /**
             * Gets the current Hook Framework type.
             *
             * @return [ExecutorType]
             */
            val type
                get() = HookApiProperty.type.takeIf { isXposedEnvironment }
                    ?: YukiXposedService.primaryFramework?.let { HookApiProperty.type(it.name) }
                    ?: HookApiProperty.type(YukiXposedModuleStatus.executorName)

            /**
             * Gets the current Hook Framework API version.
             *
             * @return [Int] -1 when unavailable.
             */
            val apiLevel
                get() = HookApiProperty.apiLevel.takeIf { isXposedEnvironment }
                    ?: YukiXposedService.primaryFramework?.apiLevel
                    ?: YukiXposedModuleStatus.executorApiLevel

            /**
             * Gets the current Hook Framework version name.
             *
             * @return [String] `unknown` when unavailable or `unsupported` when unsupported.
             */
            val versionName
                get() = HookApiProperty.versionName.takeIf { isXposedEnvironment }
                    ?: YukiXposedService.primaryFramework?.versionName
                    ?: YukiXposedModuleStatus.executorVersionName

            /**
             * Gets the current Hook Framework version code.
             *
             * @return [Int] -1 when unavailable or 0 when unsupported.
             */
            val versionCode
                get() = HookApiProperty.versionCode.takeIf { isXposedEnvironment }
                    ?: YukiXposedService.primaryFramework?.versionCode?.let {
                        it.takeIf { versionCode -> versionCode in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt() ?: -1
                    }
                    ?: YukiXposedModuleStatus.executorVersionCode
        }
    }

    /**
     * [YukiHookAPI] configuration.
     */
    object Configs {

        /**
         * Configures [YLog.Configs].
         * @param initiate the configuration block.
         */
        inline fun debugLog(initiate: YLog.Configs.() -> Unit) = YLog.Configs.apply(initiate).build()

        /**
         * Global identifier for debug logs.
         *
         * - This API is deprecated and will be removed in a future version.
         *
         * - Migrate to [debugLog] and use [YLog.Configs.tag].
         */
        @Deprecated(message = "Use the new API to implement this feature")
        var debugTag
            get() = YLog.Configs.tag
            set(value) {
                YLog.Configs.tag = value
            }

        /**
         * Whether debug mode is enabled, false by default.
         *
         * Once enabled, the log manager prints detailed Hook logs to the console.
         *
         * Disabling [YLog.Configs.isEnable] also disables [isDebug].
         */
        var isDebug = false

        /**
         * Whether debug log output is enabled.
         *
         * - This API is deprecated and will be removed in a future version.
         *
         * - Migrate to [debugLog] and use [YLog.Configs.isEnable].
         */
        @Deprecated(message = "Use the new API to implement this feature")
        var isAllowPrintingLogs
            get() = YLog.Configs.isEnable
            set(value) {
                YLog.Configs.isEnable = value
            }

        /**
         * Whether [YukiHookPrefsBridge] key-value caching is enabled.
         *
         * - This API is deprecated and will be removed in a future version.
         *
         * - Migrate to [isEnablePrefsBridgeCache].
         */
        @Deprecated(message = "Use the renamed API to implement this feature", ReplaceWith("isEnablePrefsBridgeCache"))
        var isEnableModulePrefsCache = false

        /**
         * Whether [YukiHookPrefsBridge] key-value caching is enabled.
         *
         * - This API and feature have been removed and will be deleted in a future version.
         *
         * - Direct key-value caching was removed because it can cause out-of-memory errors.
         */
        @Deprecated(message = "This API and feature have been removed. Delete this call")
        var isEnablePrefsBridgeCache = false

        /**
         * Whether caching the current Xposed module's [Resources] is enabled.
         *
         * - This feature is enabled by default to prevent excessive memory reuse.
         *
         * - When disabled, every use of [PackageParam.moduleAppResources] creates a new instance and may reduce performance.
         *
         * Call [PackageParam.refreshModuleAppResources] to refresh the cache manually.
         */
        var isEnableModuleAppResourcesCache = true

        /**
         * Whether Hook support for Xposed module activation and related states is enabled.
         *
         * - This API is deprecated and will be removed in a future version.
         *
         * - Migrate to [InjectYukiHookWithXposed.isUsingXposedModuleStatus].
         */
        @Deprecated(message = "Migrate manually to the new API")
        var isEnableHookModuleStatus = true

        /**
         * Whether Hook support for [SharedPreferences] is enabled.
         *
         * Once enabled, module startup forces [SharedPreferences] file permissions to [Context.MODE_WORLD_READABLE] (0664).
         *
         * - This optional experimental feature is disabled by default.
         *
         * - Use this only to fix file-permission errors that may remain on some systems after enabling New XSharedPreferences.
         * Do not enable it when [YukiHookPrefsBridge] already works correctly.
         */
        var isEnableHookSharedPreferences = false

        /**
         * Whether [YukiHookDataChannel] communication between the current Xposed module and host app is enabled.
         *
         * The Xposed module's [Application] must extend [ModuleApplication].
         *
         * - This feature is enabled by default. When disabled, initialization does not load [YukiHookDataChannel].
         */
        var isEnableDataChannel = true

        /**
         * Whether module APK updates may automatically hot-reload this module in already running target processes.
         *
         * This feature is disabled by default. It affects only update-triggered requests; manual requests made through
         * [Status.Service.hotReload] or [Status.Service.hotReloadAll] remain available.
         *
         * Hot reload automatically rebuilds Yuki Hooks installed while replaying the package callback. A request is rejected
         * while later runtime Yuki Hooks or an Activity Proxy are active because their lifecycle cannot be recreated safely.
         * Module-owned threads, JNI hooks, native libxposed Hooks, and other callbacks must be cleaned up and restored through
         * the optional hot reload callbacks of
         * [com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit].
         */
        var isEnableAutoHotReload = false

        /**
         * Whether the libxposed module scope is fixed and cannot be changed dynamically by users.
         *
         * When this value is directly assigned in the annotated Hook entry source, the Xposed processor writes the
         * corresponding `staticScope` value to `module.prop`. It defaults to false; changing it at runtime cannot
         * modify an already generated module metadata file.
         */
        var isStaticScope = false

        /**
         * Whether [Member] caching is enabled.
         *
         * - This API and feature have been removed and will be deleted in a future version.
         *
         * - Direct [Member] caching was removed because it can cause out-of-memory errors.
         */
        @Deprecated(message = "This API and feature have been removed. Delete this call")
        var isEnableMemberCache = false

        /** Completes the configuration block. */
        internal fun build() = Unit
    }

    /**
     * Configures [YukiHookAPI].
     *
     * See [configs Method](https://highcapable.github.io/YukiHookAPI/en/config/api-example#configs-method)
     * @param initiate the configuration block.
     */
    inline fun configs(initiate: Configs.() -> Unit) = Configs.apply(initiate).build()

    /**
     * Loading entry point for a Xposed module.
     *
     * See [Created by lambda](https://highcapable.github.io/YukiHookAPI/en/config/api-example#created-by-lambda)
     * @param initiate the Hook block.
     */
    fun encase(initiate: PackageParam.() -> Unit) {
        isLoadedFromBaseContext = false
        if (YukiXposedModule.isXposedEnvironment)
            YukiXposedModule.packageParamCallback = initiate
        else printNotFoundHookApiError()
    }

    /**
     * Loading entry point for a Xposed module.
     *
     * See [Created by Custom Hooker](https://highcapable.github.io/YukiHookAPI/en/config/api-example#created-by-custom-hooker)
     * @param hooker the required, non-empty Hooker array.
     * @throws IllegalStateException if [hooker] is empty.
     */
    fun encase(vararg hooker: YukiBaseHooker) {
        isLoadedFromBaseContext = false
        if (YukiXposedModule.isXposedEnvironment)
            YukiXposedModule.packageParamCallback = {
                if (hooker.isNotEmpty())
                    hooker.forEach { it.assignInstance(packageParam = this) }
                else YLog.innerE("Failed to passing \"encase\" method because your hooker param is empty", isImplicit = true)
            }
        else printNotFoundHookApiError()
    }

    /**
     * Loading entry point for an [Application].
     *
     * Load [YukiHookAPI] from [Application.attachBaseContext].
     *
     * See [Use as Hook API](https://highcapable.github.io/YukiHookAPI/en/guide/quick-start#use-as-hook-api)
     *
     * See [Created by lambda](https://highcapable.github.io/YukiHookAPI/en/config/api-example#created-by-lambda)
     * @param baseContext attachBaseContext.
     * @param initiate the Hook block.
     */
    fun encase(baseContext: Context?, initiate: PackageParam.() -> Unit) {
        isLoadedFromBaseContext = true
        when {
            HookApiCategoryHelper.hasAvailableHookApi && baseContext != null ->
                initiate(baseContext.createPackageParam().apply { printSplashInfo() })
            else -> printNotFoundHookApiError()
        }
    }

    /**
     * Loading entry point for an [Application].
     *
     * Load [YukiHookAPI] from [Application.attachBaseContext].
     *
     * See [Use as Hook API](https://highcapable.github.io/YukiHookAPI/en/guide/quick-start#use-as-hook-api)
     *
     * See [Created by Custom Hooker](https://highcapable.github.io/YukiHookAPI/en/config/api-example#created-by-custom-hooker)
     * @param baseContext attachBaseContext.
     * @param hooker the required, non-empty Hooker array.
     * @throws IllegalStateException if [hooker] is empty.
     */
    fun encase(baseContext: Context?, vararg hooker: YukiBaseHooker) {
        isLoadedFromBaseContext = true
        if (HookApiCategoryHelper.hasAvailableHookApi) {
            if (baseContext != null)
                if (hooker.isNotEmpty()) {
                    printSplashInfo()
                    hooker.forEach { it.assignInstance(packageParam = baseContext.createPackageParam()) }
                } else YLog.innerE("Failed to passing \"encase\" method because your hooker param is empty", isImplicit = true)
        } else printNotFoundHookApiError()
    }

    /** Prints the welcome debug log. */
    internal fun printSplashInfo() {
        if (Configs.isDebug.not() || isShowSplashLogOnceTime.not()) return
        isShowSplashLogOnceTime = false
        YLog.innerD("Welcome to YukiHookAPI $VERSION! Using ${Status.Executor.name} API ${Status.Executor.apiLevel}", isImplicit = true)
    }

    /** Prints an error when no Hook API can be found. */
    private fun printNotFoundHookApiError() =
        YLog.innerE("Could not found any available Hook APIs in current environment! Aborted", isImplicit = true)

    /**
     * Creates the Hook entry object from the base context.
     * @return [PackageParam]
     */
    private fun Context.createPackageParam() =
        PackageParam(PackageParamWrapper(HookEntryType.PACKAGE, packageName, processName, classLoader, applicationInfo))
}