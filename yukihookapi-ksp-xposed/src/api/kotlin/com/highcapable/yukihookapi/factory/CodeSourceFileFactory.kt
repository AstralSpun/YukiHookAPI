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
 * This file is created by fankes on 2022/9/20.
 */
@file:Suppress("ConstPropertyName")

package com.highcapable.yukihookapi.factory

import com.highcapable.yukihookapi.entity.GenerateData
import com.highcapable.yukihookapi.generated.YukiHookAPIProperties
import com.highcapable.yukihookapi.utils.SymbolConverterTool
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Package-name constants used by generated sources.
 */
object PackageName {
    const val YukiHookAPI_Impl = "com.highcapable.yukihookapi"
    const val ModuleApplication_Impl = "com.highcapable.yukihookapi.hook.xposed.application"
    const val YukiXposedModuleStatus_Impl = "com.highcapable.yukihookapi.hook.xposed.bridge.status"
    const val HandlerDelegateImpl_Impl = "com.highcapable.yukihookapi.hook.xposed.parasitic.activity.delegate.impl"
    const val HandlerDelegateClass = "com.highcapable.yukihookapi.hook.xposed.parasitic.activity.delegate"
    const val IActivityManagerProxyImpl_Impl = "com.highcapable.yukihookapi.hook.xposed.parasitic.activity.delegate.impl"
    const val IActivityManagerProxyClass = "com.highcapable.yukihookapi.hook.xposed.parasitic.activity.delegate"
}

/**
 * Class-name constants used by generated sources.
 */
object ClassName {
    const val YukiHookAPI_Impl = "YukiHookAPI_Impl"
    const val ModuleApplication_Impl = "ModuleApplication_Impl"
    const val YukiXposedModuleStatus_Impl = "YukiXposedModuleStatus_Impl"
    const val YukiXposedModuleStatus_Impl_Impl = "YukiXposedModuleStatus_Impl_Impl"
    const val HandlerDelegateImpl_Impl = "HandlerDelegateImpl_Impl"
    const val HandlerDelegateClass = "HandlerDelegate"
    const val IActivityManagerProxyImpl_Impl = "IActivityManagerProxyImpl_Impl"
    const val IActivityManagerProxyClass = "IActivityManagerProxy"
    const val XposedInit = "xposed_init"
    const val XposedInit_Impl = "xposed_init_Impl"
}

/**
 * Package and class names of external callers used by generated sources.
 */
object ExternalCallerName {
    val HandlerDelegateCaller = Pair(
        "com.highcapable.yukihookapi.hook.xposed.parasitic.activity.delegate.caller.HandlerDelegateCaller",
        "HandlerDelegateCaller"
    )
    val IActivityManagerProxyCaller = Pair(
        "com.highcapable.yukihookapi.hook.xposed.parasitic.activity.delegate.caller.IActivityManagerProxyCaller",
        "IActivityManagerProxyCaller"
    )
    val YukiXposedEventCaller = Pair(
        "com.highcapable.yukihookapi.hook.xposed.bridge.event.caller.YukiXposedEventCaller",
        "YukiXposedEventCaller"
    )
    val YukiXposedModuleCaller = Pair(
        "com.highcapable.yukihookapi.hook.xposed.bridge.caller.YukiXposedModuleCaller",
        "YukiXposedModuleCaller"
    )
    val YukiXposedResourcesCaller = Pair(
        "com.highcapable.yukihookapi.hook.xposed.bridge.resources.caller.YukiXposedResourcesCaller",
        "YukiXposedResourcesCaller"
    )
}

/**
 * JVM method names used by generated YukiXposedModuleStatus sources.
 */
object YukiXposedModuleStatusJvmName {
    const val IS_ACTIVE_METHOD_NAME = "__--"
    const val IS_SUPPORT_RESOURCES_HOOK_METHOD_NAME = "_--_"
    const val GET_EXECUTOR_NAME_METHOD_NAME = "_-_-"
    const val GET_EXECUTOR_API_LEVEL_METHOD_NAME = "-__-"
    const val GET_EXECUTOR_VERSION_NAME_METHOD_NAME = "-_-_"
    const val GET_EXECUTOR_VERSION_CODE_METHOD_NAME = "___-"
}

/**
 * Creates a package-specific generated name.
 * @param name the base name.
 * @return [String]
 */
private fun GenerateData.tailPackageName(name: String) = "${name}_${modulePackageName.replace(".", "_")}"

/**
 * Creates the header comment for a generated file.
 * @param currentClassTag the generated class tag.
 * @return [String]
 */
private fun createCommentContent(currentClassTag: String) =
    """
      /**
       * $currentClassTag Class
       *
       * Compiled from YukiHookXposedProcessor
       *
       * Generate Date: ${SimpleDateFormat.getDateTimeInstance().format(Date())}
       *
       * Powered by YukiHookAPI (C) HighCapable 2019
       *
       * Project URL: [${YukiHookAPIProperties.PROJECT_NAME}](${YukiHookAPIProperties.PROJECT_URL})
       */
    """.trimIndent()

/**
 * Creates the source contents of all injected files.
 * @return [Map]<[String], [String]>
 */
fun GenerateData.sources() = mapOf(
    ClassName.YukiHookAPI_Impl to """
      @file:Suppress("ClassName")
      
      package ${PackageName.YukiHookAPI_Impl}
    """.trimIndent() + "\n\n" + createCommentContent(ClassName.YukiHookAPI_Impl) + "\n" + """
      object ${ClassName.YukiHookAPI_Impl} {
      
          val compiledTimestamp get() = ${System.currentTimeMillis()}
      }
    """.trimIndent(),
    ClassName.ModuleApplication_Impl to """
      @file:Suppress("ClassName")
      
      package ${PackageName.ModuleApplication_Impl}
      
      import ${SymbolConverterTool.process(entryPackageName)}.$entryClassName
    """.trimIndent() + "\n\n" + createCommentContent(ClassName.ModuleApplication_Impl) + "\n" + """
      object ${ClassName.ModuleApplication_Impl} {
      
          fun callHookEntryInit() = try {
              ${if (isEntryClassKindOfObject) "$entryClassName.onInit()" else "$entryClassName().onInit()"}
          } catch (_: Throwable) {
          }
      }
    """.trimIndent(),
    ClassName.YukiXposedModuleStatus_Impl to """
      @file:Suppress("ClassName")
      
      package ${PackageName.YukiXposedModuleStatus_Impl}
      
    """.trimIndent() + "\n\n" + createCommentContent(ClassName.YukiXposedModuleStatus_Impl) + "\n" + """
      object ${ClassName.YukiXposedModuleStatus_Impl} {

          val className get() = "${PackageName.YukiXposedModuleStatus_Impl}.${tailPackageName(ClassName.YukiXposedModuleStatus_Impl_Impl)}"
      }
    """.trimIndent(),
    ClassName.YukiXposedModuleStatus_Impl_Impl to """
      @file:Suppress("ClassName")
      
      package ${PackageName.YukiXposedModuleStatus_Impl}
      
      import android.util.Log
      import androidx.annotation.Keep
    """.trimIndent() + "\n\n" + createCommentContent(ClassName.YukiXposedModuleStatus_Impl) + "\n" + """
      @Keep
      object ${tailPackageName(ClassName.YukiXposedModuleStatus_Impl_Impl)} {
      
          @JvmStatic
          @JvmName("${YukiXposedModuleStatusJvmName.IS_ACTIVE_METHOD_NAME}")
          fun function${(1000..99999).random()}(): Boolean {
              phe()
              return false
          }
      
          @JvmStatic
          @JvmName("${YukiXposedModuleStatusJvmName.IS_SUPPORT_RESOURCES_HOOK_METHOD_NAME}")
          fun function${(1000..99999).random()}(): Boolean {
              phe()
              return false
          }
      
          @JvmStatic
          @JvmName("${YukiXposedModuleStatusJvmName.GET_EXECUTOR_NAME_METHOD_NAME}")
          fun function${(1000..99999).random()}(): String {
              phe()
              return "unknown"
          }
      
          @JvmStatic
          @JvmName("${YukiXposedModuleStatusJvmName.GET_EXECUTOR_API_LEVEL_METHOD_NAME}")
          fun function${(1000..99999).random()}(): Int {
              phe()
              return -1
          }
      
          @JvmStatic
          @JvmName("${YukiXposedModuleStatusJvmName.GET_EXECUTOR_VERSION_NAME_METHOD_NAME}")
          fun function${(1000..99999).random()}(): String {
              phe()
              return "unknown"
          }
      
          @JvmStatic
          @JvmName("${YukiXposedModuleStatusJvmName.GET_EXECUTOR_VERSION_CODE_METHOD_NAME}")
          fun function${(1000..99999).random()}(): Int {
              phe()
              return -1
          }
      
          @JvmStatic
          @JvmName("_${(1000..99999).random()}")
          private fun phe() {
              /** Consume a long method body */
              if (System.currentTimeMillis() == 0L) Log.d("${(1000..9999).random()}", "${(100000..999999).random()}")
          }
      }
    """.trimIndent(),
    ClassName.HandlerDelegateImpl_Impl to """
      @file:Suppress("ClassName", "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
      
      package ${PackageName.HandlerDelegateImpl_Impl}
      
      import android.os.Handler
      import ${PackageName.HandlerDelegateClass}.${tailPackageName(ClassName.HandlerDelegateClass)}
    """.trimIndent() + "\n\n" + createCommentContent(ClassName.HandlerDelegateImpl_Impl) + "\n" + """
      object ${ClassName.HandlerDelegateImpl_Impl} {
      
          val wrapperClassName get() = "${PackageName.HandlerDelegateClass}.${tailPackageName(ClassName.HandlerDelegateClass)}"
      
          fun createWrapper(baseInstance: Handler.Callback? = null): Handler.Callback = ${tailPackageName(ClassName.HandlerDelegateClass)}(baseInstance)
      }
    """.trimIndent(),
    ClassName.HandlerDelegateClass to """
      @file:Suppress("ClassName", "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
      
      package ${PackageName.HandlerDelegateClass}
      
      import android.os.Handler
      import android.os.Message
      import androidx.annotation.Keep
      import ${ExternalCallerName.HandlerDelegateCaller.first}
    """.trimIndent() + "\n\n" + createCommentContent(ClassName.HandlerDelegateClass) + "\n" + """
      @Keep
      class ${tailPackageName(ClassName.HandlerDelegateClass)}(private val baseInstance: Handler.Callback?) : Handler.Callback {
      
          override fun handleMessage(msg: Message) = ${ExternalCallerName.HandlerDelegateCaller.second}.callHandleMessage(baseInstance, msg)
      }
    """.trimIndent(),
    ClassName.IActivityManagerProxyImpl_Impl to """
      @file:Suppress("ClassName", "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
      
      package ${PackageName.IActivityManagerProxyImpl_Impl}
      
      import android.os.Handler
      import ${ExternalCallerName.IActivityManagerProxyCaller.first}
      import ${PackageName.IActivityManagerProxyClass}.${tailPackageName(ClassName.IActivityManagerProxyClass)}
      import java.lang.reflect.Proxy
    """.trimIndent() + "\n\n" + createCommentContent(ClassName.IActivityManagerProxyImpl_Impl) + "\n" + """
      object ${ClassName.IActivityManagerProxyImpl_Impl} {
      
          fun createWrapper(clazz: Class<*>?, instance: Any) = 
              Proxy.newProxyInstance(${ExternalCallerName.IActivityManagerProxyCaller.second}.currentClassLoader, arrayOf(clazz), ${
        tailPackageName(
            ClassName.IActivityManagerProxyClass
        )
    }(instance))
      }
    """.trimIndent(),
    ClassName.IActivityManagerProxyClass to """
      @file:Suppress("ClassName", "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
      
      package ${PackageName.IActivityManagerProxyClass}
      
      import androidx.annotation.Keep
      import ${ExternalCallerName.IActivityManagerProxyCaller.first}
      import java.lang.reflect.InvocationHandler
      import java.lang.reflect.Method
      import java.lang.reflect.Proxy
    """.trimIndent() + "\n\n" + createCommentContent(ClassName.IActivityManagerProxyClass) + "\n" + """
      @Keep
      class ${tailPackageName(ClassName.IActivityManagerProxyClass)}(private val baseInstance: Any) : InvocationHandler {
      
          override fun invoke(proxy: Any?, method: Method?, args: Array<Any>?) = ${ExternalCallerName.IActivityManagerProxyCaller.second}.callInvoke(baseInstance, method, args)
      }
    """.trimIndent(),
    ClassName.XposedInit to """
      @file:Suppress("ClassName", "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
      
      package ${SymbolConverterTool.process(entryPackageName)}
      
      import ${ExternalCallerName.YukiXposedEventCaller.first}
      import io.github.libxposed.api.XposedModule
      import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
      import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
      import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
      import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
      import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
      import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
    """.trimIndent() + "\n\n" + createCommentContent("Xposed Init") + "\n" + """
      
      class $xInitClassName : XposedModule() {
      
          override fun onModuleLoaded(param: ModuleLoadedParam) {
              ${entryClassName}_Impl.callOnModuleLoaded(this, param)
              ${ExternalCallerName.YukiXposedEventCaller.second}.callOnModuleLoaded(param)
          }
      
          override fun onPackageLoaded(param: PackageLoadedParam) {
              ${ExternalCallerName.YukiXposedEventCaller.second}.callOnPackageLoaded(param)
          }

          override fun onPackageReady(param: PackageReadyParam) {
              ${entryClassName}_Impl.callOnPackageReady(param)
              ${ExternalCallerName.YukiXposedEventCaller.second}.callOnPackageReady(param)
          }

          override fun onSystemServerStarting(param: SystemServerStartingParam) {
              ${entryClassName}_Impl.callOnSystemServerStarting(param)
              ${ExternalCallerName.YukiXposedEventCaller.second}.callOnSystemServerStarting(param)
          }

          override fun onHotReloading(param: HotReloadingParam) = ${entryClassName}_Impl.callOnHotReloading(param)

          override fun onHotReloaded(param: HotReloadedParam) {
              ${entryClassName}_Impl.callOnHotReloaded(this, param)
          }
      }
    """.trimIndent(),
    ClassName.XposedInit_Impl to """
      @file:Suppress("ClassName", "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
      
      package ${SymbolConverterTool.process(entryPackageName)}
      
      import ${ExternalCallerName.YukiXposedModuleCaller.first}
      import com.highcapable.yukihookapi.hook.xposed.bridge.type.HookEntryType
      import io.github.libxposed.api.XposedInterface
      import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
      import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
      import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
      import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
      import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
    """.trimIndent() + "\n\n" + createCommentContent("Xposed Init Impl") + "\n" + """
      object ${entryClassName}_Impl {
      
          private const val MODULE_PACKAGE_NAME = "${customMPackageName.ifBlank { modulePackageName }}"
          private var isModuleLoaded = false
          private var isHotReloadEntryAttached = false
          private var hotReloadState: Any? = null
          private var processName = ""
          private val hookEntry by lazy { ${if (isEntryClassKindOfObject) entryClassName else "$entryClassName()"} }
      
          fun callOnModuleLoaded(base: XposedInterface, param: ModuleLoadedParam) {
              if (isModuleLoaded) return
              runCatching {
                  processName = param.processName
                  ${ExternalCallerName.YukiXposedModuleCaller.second}.callOnStartLoadModule(
                      base = base,
                      packageName = MODULE_PACKAGE_NAME,
                      appFilePath = base.moduleApplicationInfo.sourceDir
                  )
                  hookEntry.onXposedEvent()
                  hookEntry.onInit()
                  if (${ExternalCallerName.YukiXposedModuleCaller.second}.isXposedCallbackSetUp) {
                      ${ExternalCallerName.YukiXposedModuleCaller.second}.callLogError("You cannot load a hooker in \"onInit\" or \"onXposedEvent\" method! Aborted")
                      return
                  }
                  hookEntry.onHook()
                  ${ExternalCallerName.YukiXposedModuleCaller.second}.callOnFinishLoadModule()
                  isModuleLoaded = true
              }.onFailure {
                  ${ExternalCallerName.YukiXposedModuleCaller.second}.callLogError("An exception occurred when YukiHookAPI loading Xposed Module", it)
              }
              if (isModuleLoaded.not()) return
              ${ExternalCallerName.YukiXposedModuleCaller.second}.callOnPackageLoaded(
                  type = HookEntryType.ZYGOTE,
                  packageName = null,
                  processName = processName,
                  appClassLoader = ClassLoader.getSystemClassLoader()
              )
          }
      
          fun callOnPackageReady(param: PackageReadyParam) {
              if (isModuleLoaded.not()) return
              ${ExternalCallerName.YukiXposedModuleCaller.second}.callOnPackageLoaded(
                  type = HookEntryType.PACKAGE,
                  packageName = param.packageName,
                  processName = processName,
                  appClassLoader = param.classLoader,
                  appInfo = param.applicationInfo
              )
          }
      
          fun callOnSystemServerStarting(param: SystemServerStartingParam) {
              if (isModuleLoaded.not()) return
              ${ExternalCallerName.YukiXposedModuleCaller.second}.callOnPackageLoaded(
                  type = HookEntryType.PACKAGE,
                  packageName = "android",
                  processName = processName,
                  appClassLoader = param.classLoader
              )
          }

          fun callOnHotReloading(param: HotReloadingParam): Boolean {
              if (isModuleLoaded.not() && isHotReloadEntryAttached.not()) return false
              return runCatching {
                  if (${ExternalCallerName.YukiXposedModuleCaller.second}.callIsHotReloadAllowed(param.extras).not()) return false
                  val savedInstanceState = ${ExternalCallerName.YukiXposedModuleCaller.second}.callOnHotReloading(hotReloadState)
                  param.setSavedInstanceState(savedInstanceState)
                  hookEntry.onHotReloading(${ExternalCallerName.YukiXposedModuleCaller.second}.callSanitizeHotReloadExtras(param.extras))
                  ${ExternalCallerName.YukiXposedModuleCaller.second}.callOnHotReloadingAccepted()
                  true
              }.getOrElse {
                  ${ExternalCallerName.YukiXposedModuleCaller.second}.callLogError("An exception occurred while preparing YukiHookAPI hot reload", it)
                  false
              }
          }

          fun callOnHotReloaded(base: XposedInterface, param: HotReloadedParam) {
              if (isModuleLoaded || isHotReloadEntryAttached) return
              isHotReloadEntryAttached = true
              hotReloadState = param.savedInstanceState
              try {
                  ${ExternalCallerName.YukiXposedModuleCaller.second}.callOnStartHotReload(param.oldHookHandles)
                  processName = param.processName
                  ${ExternalCallerName.YukiXposedModuleCaller.second}.callOnStartLoadModule(
                      base = base,
                      packageName = MODULE_PACKAGE_NAME,
                      appFilePath = base.moduleApplicationInfo.sourceDir
                  )
                  hookEntry.onXposedEvent()
                  hookEntry.onInit()
                  if (${ExternalCallerName.YukiXposedModuleCaller.second}.isXposedCallbackSetUp) {
                      error("You cannot load a hooker in onInit or onXposedEvent during hot reload")
                  }
                  hookEntry.onHook()
                  ${ExternalCallerName.YukiXposedModuleCaller.second}.callOnFinishLoadModule()
                  ${ExternalCallerName.YukiXposedModuleCaller.second}.callOnHotReloaded(param.savedInstanceState)
                  hookEntry.onHotReloaded(${ExternalCallerName.YukiXposedModuleCaller.second}.callSanitizeHotReloadExtras(param.extras))
                  ${ExternalCallerName.YukiXposedModuleCaller.second}.callOnFinishHotReload()
                  isModuleLoaded = true
                  hotReloadState = null
              } catch (throwable: Throwable) {
                  ${ExternalCallerName.YukiXposedModuleCaller.second}.callOnAbortHotReload(param.oldHookHandles)
                  ${ExternalCallerName.YukiXposedModuleCaller.second}.callLogError(
                      "An exception occurred while applying YukiHookAPI hot reload",
                      throwable
                  )
                  throw throwable
              }
          }
      }
    """.trimIndent()
)