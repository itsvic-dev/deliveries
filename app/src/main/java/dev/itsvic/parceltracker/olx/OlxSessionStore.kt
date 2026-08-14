// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.olx

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

internal class OlxSessionStore(context: Context) {
  private val preferences =
      context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  fun load(): OlxSession? {
    val encrypted = preferences.getString(KEY_SESSION, null) ?: return null
    return runCatching { sessionFromJson(decrypt(encrypted)) }
        .getOrElse {
          clearSession()
          null
        }
  }

  fun save(session: OlxSession) {
    preferences.edit { putString(KEY_SESSION, encrypt(sessionToJson(session))) }
  }

  fun clearSession() {
    preferences.edit { remove(KEY_SESSION) }
  }

  fun loadPendingAuthorization(): OlxAuthorizationRequest? {
    val encrypted = preferences.getString(KEY_PENDING_AUTHORIZATION, null) ?: return null
    return runCatching {
          val json = decrypt(encrypted)
          OlxAuthorizationRequest(
              authorizationUrl = json.getString("authorizationUrl"),
              codeVerifier = json.getString("codeVerifier"),
              state = json.getString("state"),
          )
        }
        .getOrElse {
          clearPendingAuthorization()
          null
        }
  }

  fun savePendingAuthorization(request: OlxAuthorizationRequest) {
    val json =
        JSONObject().apply {
          put("authorizationUrl", request.authorizationUrl)
          put("codeVerifier", request.codeVerifier)
          put("state", request.state)
        }
    preferences.edit { putString(KEY_PENDING_AUTHORIZATION, encrypt(json)) }
  }

  fun clearPendingAuthorization() {
    preferences.edit { remove(KEY_PENDING_AUTHORIZATION) }
  }

  fun deviceId(): String {
    preferences.getString(KEY_DEVICE_ID, null)?.let { return it }
    val prefix =
        ByteArray(16)
            .also(SecureRandom()::nextBytes)
            .joinToString("") { "%02X".format(it.toInt() and 0xff) }
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    val random = SecureRandom()
    val suffix = buildString { repeat(20) { append(alphabet[random.nextInt(alphabet.length)]) } }
    return "$prefix$suffix".also {
      preferences.edit { putString(KEY_DEVICE_ID, it) }
    }
  }

  private fun encryptionKey(): SecretKey {
    val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
      init(
          KeyGenParameterSpec.Builder(
                  KEY_ALIAS,
                  KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
              )
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .setKeySize(256)
              .build())
      generateKey()
    }
  }

  private fun encrypt(json: JSONObject): String {
    val cipher =
        Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, encryptionKey()) }
    val ciphertext = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
    val result = byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + ciphertext
    return Base64.encodeToString(result, Base64.NO_WRAP)
  }

  private fun decrypt(encrypted: String): JSONObject {
    val bytes = Base64.decode(encrypted, Base64.NO_WRAP)
    val ivLength = bytes.first().toInt()
    require(ivLength in 12..16 && bytes.size > ivLength + 1)
    val iv = bytes.copyOfRange(1, ivLength + 1)
    val ciphertext = bytes.copyOfRange(ivLength + 1, bytes.size)
    val cipher =
        Cipher.getInstance(TRANSFORMATION).apply {
          init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(128, iv))
        }
    return JSONObject(String(cipher.doFinal(ciphertext), Charsets.UTF_8))
  }

  private fun sessionToJson(session: OlxSession) =
      JSONObject().apply {
        put("accessToken", session.accessToken)
        put("refreshToken", session.refreshToken)
        put("idToken", session.idToken)
        put("expiresAt", session.expiresAtEpochMillis)
        put("accountId", session.accountId)
        put("displayName", session.displayName)
      }

  private fun sessionFromJson(json: JSONObject) =
      OlxSession(
          accessToken = json.getString("accessToken"),
          refreshToken = json.getString("refreshToken"),
          idToken =
              if (json.isNull("idToken")) null
              else json.optString("idToken").takeIf(String::isNotBlank),
          expiresAtEpochMillis = json.getLong("expiresAt"),
          accountId = json.getString("accountId"),
          displayName = json.getString("displayName"),
      )

  private companion object {
    const val PREFERENCES_NAME = "olx_session"
    const val KEY_SESSION = "session"
    const val KEY_PENDING_AUTHORIZATION = "pending_authorization"
    const val KEY_DEVICE_ID = "device_id"
    const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    const val KEY_ALIAS = "parcel_tracker_olx_session"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
  }
}
