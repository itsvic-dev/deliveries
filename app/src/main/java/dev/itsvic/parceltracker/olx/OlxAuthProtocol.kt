// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.olx

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

internal object OlxAuthProtocol {
  const val CLIENT_ID = "5e8cu02ul6chdbm48a3j31vg9k"
  const val REDIRECT_URI = "olxauth://olx.pl/login"
  const val TOKEN_ENDPOINT = "https://login.olx.pl/oauth2/token"

  private const val AUTHORIZATION_ENDPOINT = "https://login.olx.pl/oauth2/authorize"
  private val secureRandom = SecureRandom()

  fun createAuthorizationRequest(): OlxAuthorizationRequest {
    val verifier = randomUrlSafeString(96)
    val sessionId = randomUrlSafeString(15)
    val stateJson = "{\"s\":\"$sessionId\",\"sl\":\"$sessionId\"}"
    val state = Base64.getEncoder().encodeToString(stateJson.toByteArray(StandardCharsets.UTF_8))
    val parameters =
        linkedMapOf(
            "client_id" to CLIENT_ID,
            "redirect_uri" to REDIRECT_URI,
            "code_challenge" to codeChallenge(verifier),
            "code_challenge_method" to "S256",
            "response_type" to "code",
            "st" to state,
            "lang" to "pl",
            "theme" to "dark",
        )
    return OlxAuthorizationRequest(
        authorizationUrl = "$AUTHORIZATION_ENDPOINT?${encodeForm(parameters)}",
        codeVerifier = verifier,
        state = state,
    )
  }

  fun isCallbackUrl(url: String): Boolean =
      runCatching {
            val uri = URI(url)
            uri.scheme.equals("olxauth", ignoreCase = true) &&
                uri.host.equals("olx.pl", ignoreCase = true) &&
                uri.path == "/login"
          }
          .getOrDefault(false)

  fun authorizationCodeFromCallback(url: String): String? {
    if (!isCallbackUrl(url)) return null
    // OLX returns only the code here; PKCE binds it to the encrypted pending verifier.
    return parseQuery(URI(url).rawQuery)["code"]?.takeIf(String::isNotBlank)
  }

  fun codeChallenge(verifier: String): String {
    val digest =
        MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(StandardCharsets.US_ASCII))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
  }

  private fun encodeForm(parameters: Map<String, String>): String =
      parameters.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }

  private fun randomUrlSafeString(byteCount: Int): String {
    val bytes = ByteArray(byteCount).also(secureRandom::nextBytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
  }

  private fun parseQuery(query: String?): Map<String, String> {
    if (query.isNullOrEmpty()) return emptyMap()
    return query
        .split('&')
        .mapNotNull { part ->
          val separator = part.indexOf('=')
          if (separator < 0) return@mapNotNull null
          decode(part.substring(0, separator)) to decode(part.substring(separator + 1))
        }
        .toMap()
  }

  private fun encode(value: String): String =
      URLEncoder.encode(value, StandardCharsets.UTF_8.name())

  private fun decode(value: String): String =
      URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}
