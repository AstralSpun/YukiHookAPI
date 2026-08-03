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
package com.highcapable.yukihookapi.hook.dexkit.internal

import io.fastkv.interfaces.FastCipher
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal class DexResolverCipher(password: String) : FastCipher {

    private val key = SecretKeySpec(
        MessageDigest.getInstance("SHA-256").digest(password.toByteArray(StandardCharsets.UTF_8)),
        "AES"
    )
    private val random = SecureRandom()

    override fun encrypt(src: ByteArray): ByteArray {
        val iv = ByteArray(IV_SIZE).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, iv))
        }
        return iv + cipher.doFinal(src)
    }

    override fun decrypt(dst: ByteArray): ByteArray {
        require(dst.size > IV_SIZE)
        val iv = dst.copyOf(IV_SIZE)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, iv))
        }
        return cipher.doFinal(dst, IV_SIZE, dst.size - IV_SIZE)
    }

    override fun encrypt(src: Int) = src

    override fun decrypt(dst: Int) = dst

    override fun encrypt(src: Long) = src

    override fun decrypt(dst: Long) = dst

    companion object {

        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val TAG_LENGTH = 128
    }
}