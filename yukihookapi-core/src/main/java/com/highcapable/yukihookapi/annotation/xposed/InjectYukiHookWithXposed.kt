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
 * This file is created by fankes on 2022/2/3.
 */
package com.highcapable.yukihookapi.annotation.xposed

import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit

/**
 * Marks the [YukiHookAPI] Xposed entry class for code generation.
 *
 * - The project source directory defaults to `src/main` and can be customized through [sourcePath].
 *
 * - The processor creates `java_init.list` and `module.prop` under `[sourcePath]/resources/META-INF/xposed`.
 * The generated `module.prop` writes `autoHotReload` from a directly declared boolean assignment in the Hook entry
 * source when one is present; otherwise it remains disabled. Update-triggered requests are still accepted only when
 * [YukiHookAPI.Configs.isEnableAutoHotReload] is enabled at runtime by the Hook entry.
 *
 * The generated libxposed entry uses the fully qualified annotated class name plus `_YukiHookXposedInit`, or [entryClassName].
 *
 * - [modulePackageName] overrides the detected module package. Otherwise AndroidManifest.xml and Gradle metadata are analyzed.
 *
 * - A custom [modulePackageName] produces a compile-time warning so the value can be verified.
 *
 * - Do not modify the generated `java_init.list` manually. Invalid content may prevent the module from loading.
 *
 * - The annotated class must implement [IYukiHookXposedInit] and [IYukiHookXposedInit.onHook].
 *
 * - Exactly one Hook entry may be annotated. Multiple entries produce a compile-time error.
 *
 * See [InjectYukiHookWithXposed Annotation](https://highcapable.github.io/YukiHookAPI/en/config/xposed-using#injectyukihookwithxposed-annotation)
 * @param sourcePath the project-relative source path, defaults to `src/main`.
 * @param modulePackageName the module package name, or an empty string for automatic detection.
 * @param entryClassName the generated Xposed entry class name, or an empty string to use `AnnotatedClass_YukiHookXposedInit`.
 * @param isUsingXposedModuleStatus whether self-hook based Xposed module status detection is enabled, defaults to true. Under libxposed, the module
 * package must be included in scope; module apps outside scope should query libxposed service instead.
 * @param isUsingResourcesHook retained for source compatibility. Legacy resource replacement and layout hooks are unavailable in libxposed API 102.
 */
@Target(AnnotationTarget.CLASS)
annotation class InjectYukiHookWithXposed(
    val sourcePath: String = "src/main",
    val modulePackageName: String = "",
    val entryClassName: String = "",
    val isUsingXposedModuleStatus: Boolean = true,
    val isUsingResourcesHook: Boolean = false
)