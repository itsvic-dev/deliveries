// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.allegro

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.squareup.moshi.Moshi
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AllegroSessionStore(context: Context) {
  private val appContext = context.applicationContext
  private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
  private val adapter = Moshi.Builder().build().adapter(AllegroSession::class.java)

  @Synchronized
  fun load(): AllegroSession? {
    val encrypted = preferences.getString(SESSION_KEY, null) ?: return null
    return try {
      val parts = encrypted.split('.', limit = 2)
      require(parts.size == 2)
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(
          Cipher.DECRYPT_MODE,
          getOrCreateKey(),
          GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)),
      )
      cipher.updateAAD(appContext.packageName.toByteArray())
      val json = String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
      adapter.fromJson(json)
    } catch (_: Exception) {
      clear()
      null
    }
  }

  @Synchronized
  fun save(session: AllegroSession) {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
    cipher.updateAAD(appContext.packageName.toByteArray())
    val ciphertext = cipher.doFinal(adapter.toJson(session).toByteArray(Charsets.UTF_8))
    val value =
        "${Base64.encodeToString(cipher.iv, Base64.NO_WRAP)}.${Base64.encodeToString(ciphertext, Base64.NO_WRAP)}"
    preferences.edit().putString(SESSION_KEY, value).apply()
  }

  @Synchronized fun clear() = preferences.edit().clear().apply()

  private fun getOrCreateKey(): SecretKey {
    val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let {
      return it
    }

    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
      init(
          KeyGenParameterSpec.Builder(
                  KEY_ALIAS,
                  KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
              )
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .build())
      generateKey()
    }
  }

  companion object {
    const val PREFERENCES_NAME = "allegro_session"
    private const val SESSION_KEY = "encrypted_session"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "deliveries_allegro_session"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
  }
}
