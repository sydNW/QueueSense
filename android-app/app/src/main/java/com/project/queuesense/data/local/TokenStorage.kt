
package com.project.queuesense.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class TokenStorage(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences("queuesense_secure_session", Context.MODE_PRIVATE)

    private val keyAlias = "queuesense_token_key"

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        val existingKey = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existingKey != null) return existingKey

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )

        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )

        return keyGenerator.generateKey()
    }

    fun saveToken(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())

        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv

        val storedValue = Base64.encodeToString(
            iv + encrypted,
            Base64.NO_WRAP
        )

        preferences.edit()
            .putString("auth_token", storedValue)
            .apply()
    }

    fun getToken(): String? {
        val storedValue = preferences.getString("auth_token", null)
            ?: return null

        return try {
            val combined = Base64.decode(storedValue, Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, 12)
            val encrypted = combined.copyOfRange(12, combined.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getSecretKey(),
                GCMParameterSpec(128, iv)
            )

            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun clearToken() {
        preferences.edit().remove("auth_token").apply()
    }
}
