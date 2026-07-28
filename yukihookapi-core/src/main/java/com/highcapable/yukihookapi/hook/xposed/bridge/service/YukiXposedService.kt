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
package com.highcapable.yukihookapi.hook.xposed.bridge.service

import android.content.SharedPreferences
import com.highcapable.yukihookapi.YukiHookAPI.Status.Service.Framework
import com.highcapable.yukihookapi.YukiHookAPI.Status.Service.RunningTarget
import com.highcapable.yukihookapi.YukiHookAPI.Status.Service.RunningTargetState
import com.highcapable.yukihookapi.YukiHookAPI.Status.Service.ScopeRequestResult
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.libxposed.service.HookedTarget
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

/** libxposed service bridge used only in the module application process. */
internal object YukiXposedService {

    private val lock = Any()

    /** Connected framework services in binding order. */
    private val services = linkedMapOf<XposedService, Long>()

    /** Last process-local service connection identity. */
    private var lastServiceId = 0L

    /** Whether the process-wide service listener has been registered. */
    @Volatile
    private var isRegistered = false

    /** Process-wide service lifecycle listener. */
    private val listener = object : XposedServiceHelper.OnServiceListener {

        override fun onServiceBind(service: XposedService) {
            synchronized(lock) {
                if (service !in services) services[service] = ++lastServiceId
            }
        }

        override fun onServiceDied(service: XposedService) {
            synchronized(lock) { services -= service }
        }
    }

    /** Registers the process-wide libxposed service listener once. */
    internal fun register() {
        synchronized(lock) {
            if (isRegistered) return
            isRegistered = true
        }
        runCatching { XposedServiceHelper.registerListener(listener) }.onFailure {
            synchronized(lock) { isRegistered = false }
            YLog.innerE("Failed to register libxposed service listener", it)
        }
    }

    /** Whether at least one framework service is responsive. */
    internal val isAvailable get() = serviceEntries().isNotEmpty()

    /** Information about the first responsive framework service. */
    internal val primaryFramework get() = serviceEntries().firstOrNull()?.second

    /** Information about all responsive framework services. */
    internal val frameworks get() = serviceEntries().map { it.second }

    /** Module scope reported by each responsive framework service. */
    internal val scopes
        get() = linkedMapOf<Framework, List<String>>().apply {
            serviceEntries().forEach { (service, framework) ->
                runCatching { this[framework] = service.scope.toList() }
                    .onFailure { logServiceFailure(framework, "query module scope", it) }
            }
        }

    /** Running hooked targets reported by API 102 framework services. */
    internal val runningTargets
        get() = buildList {
            serviceEntries().forEach { (service, framework) ->
                if (framework.apiLevel < XposedService.API_102) return@forEach
                runCatching {
                    service.runningTargets.forEach { target ->
                        add(
                            RunningTarget(
                                framework = framework,
                                uid = target.uid,
                                pid = target.pid,
                                processName = target.processName,
                                state = target.state.toPublicState(),
                                loadedVersionCode = target.loadedVersionCode
                            )
                        )
                    }
                }.onFailure { logServiceFailure(framework, "query running targets", it) }
            }
        }

    /** Gets writable remote preferences from all capable framework services. */
    internal fun remotePreferences(group: String) = snapshotServices().mapNotNull { service ->
        runCatching {
            service.takeIf { it.frameworkProperties and XposedService.PROP_CAP_REMOTE != 0L }
                ?.getRemotePreferences(group)
        }.onFailure { YLog.innerE("Failed to access libxposed remote preferences group $group", it) }.getOrNull()
    }.distinct()

    /** Requests module scope from every responsive framework service. */
    internal fun requestScope(packages: List<String>, callback: (ScopeRequestResult) -> Unit): Boolean {
        var isDispatched = false
        serviceEntries().forEach { (service, framework) ->
            runCatching {
                service.requestScope(packages, object : XposedService.OnScopeEventListener {

                    override fun onScopeRequestApproved(approved: MutableList<String>) {
                        dispatchScopeCallback(
                            callback,
                            ScopeRequestResult(framework, isApproved = true, approvedPackages = approved.toList())
                        )
                    }

                    override fun onScopeRequestFailed(message: String) {
                        dispatchScopeCallback(
                            callback,
                            ScopeRequestResult(framework, isApproved = false, failureMessage = message)
                        )
                    }
                })
            }.onSuccess { isDispatched = true }
                .onFailure {
                    logServiceFailure(framework, "request module scope", it)
                    dispatchScopeCallback(
                        callback,
                        ScopeRequestResult(
                            framework,
                            isApproved = false,
                            failureMessage = it.message ?: "Failed to request module scope"
                        )
                    )
                }
        }
        return isDispatched
    }

    /** Removes module scope from every responsive framework service. */
    internal fun removeScope(packages: List<String>): Boolean {
        var isDispatched = false
        serviceEntries().forEach { (service, framework) ->
            runCatching { service.removeScope(packages) }
                .onSuccess { isDispatched = true }
                .onFailure { logServiceFailure(framework, "remove module scope", it) }
        }
        return isDispatched
    }

    /** Creates service and framework information pairs from a stable service snapshot. */
    private fun serviceEntries() = snapshotServiceEntries().mapNotNull { (service, id) ->
        runCatching { service to service.frameworkInfo(id) }
            .onFailure { YLog.innerE("Failed to query libxposed framework information", it) }.getOrNull()
    }

    /** Creates immutable public framework information. */
    private fun XposedService.frameworkInfo(id: Long) = Framework(
        id = id,
        name = frameworkName,
        apiLevel = apiVersion,
        versionName = frameworkVersion,
        versionCode = frameworkVersionCode,
        properties = frameworkProperties
    )

    /** Creates a stable snapshot without holding the service collection lock during Binder calls. */
    private fun snapshotServices() = synchronized(lock) { services.keys.toList() }

    /** Creates a stable service and identity snapshot without retaining the service collection lock during Binder calls. */
    private fun snapshotServiceEntries() = synchronized(lock) { services.toList() }

    /** Maps libxposed target state to the public YukiHookAPI state. */
    private fun HookedTarget.State.toPublicState() = when (this) {
        HookedTarget.State.UP_TO_DATE -> RunningTargetState.UP_TO_DATE
        HookedTarget.State.STALE -> RunningTargetState.STALE
        HookedTarget.State.RELOADING -> RunningTargetState.RELOADING
        HookedTarget.State.FAILED -> RunningTargetState.FAILED
    }

    /** Dispatches a user callback without allowing it to escape a Binder callback. */
    private fun dispatchScopeCallback(callback: (ScopeRequestResult) -> Unit, result: ScopeRequestResult) {
        runCatching { callback(result) }.onFailure { YLog.innerE("An exception occurred in the module scope callback", it) }
    }

    /** Logs an operation failure with the corresponding framework identity. */
    private fun logServiceFailure(framework: Framework, operation: String, throwable: Throwable) {
        YLog.innerE("Failed to $operation through ${framework.name}", throwable)
    }
}