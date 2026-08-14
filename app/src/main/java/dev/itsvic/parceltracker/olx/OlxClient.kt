// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.olx

import android.os.Build
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync

internal class OlxClient(
    private val store: OlxSessionStore,
    private val authUrl: HttpUrl = OlxAuthProtocol.TOKEN_ENDPOINT.toHttpUrl(),
    private val profileUrl: HttpUrl = "https://www.olx.pl/api/v1/users/me/".toHttpUrl(),
    private val paymentsUrl: HttpUrl = "https://pl.ps.prd.eu.olx.org/".toHttpUrl(),
    debugInterceptor: Interceptor? = null,
) {
  private val json = Json { ignoreUnknownKeys = true }
  private val client =
      OkHttpClient.Builder()
          .connectTimeout(20, TimeUnit.SECONDS)
          .readTimeout(30, TimeUnit.SECONDS)
          .apply { debugInterceptor?.let(::addInterceptor) }
          .build()

  suspend fun exchangeCode(
      request: OlxAuthorizationRequest,
      code: String,
  ): OlxSession {
    val tokens =
        requestTokens(
            linkedMapOf(
                "grant_type" to "authorization_code",
                "client_id" to OlxAuthProtocol.CLIENT_ID,
                "redirect_uri" to OlxAuthProtocol.REDIRECT_URI,
                "code" to code,
                "code_verifier" to request.codeVerifier,
                "st" to request.state,
            ))
    return createSession(tokens, null)
  }

  suspend fun purchases(): List<OlxOrderDto> {
    val result = mutableListOf<OlxOrderDto>()
    var offset = 0
    do {
      val url =
          paymentsUrl.newBuilder()
              .addPathSegments("order-overview/v1/purchases")
              .addQueryParameter("limit", PAGE_SIZE.toString())
              .addQueryParameter("offset", offset.toString())
              .build()
      val response = authenticatedGet(url)
      val page = OlxParser.parsePurchases(response)
      result += page.entries
      offset += page.entries.size
    } while (page.entries.isNotEmpty() && offset < page.total)
    return result
  }

  private suspend fun authenticatedGet(url: HttpUrl): String {
    var session = freshSession()
    var response = execute(apiRequest(url, session))
    if (response.status == 401) {
      session = refreshMutex.withLock { refresh(store.load() ?: session, force = true) }
      response = execute(apiRequest(url, session))
    }
    if (response.status == 401) {
      store.clearSession()
      throw OlxSessionExpiredException("Your OLX session expired. Sign in again.")
    }
    if (response.status !in 200..299) {
      throw OlxException("OLX request failed (HTTP ${response.status}).", response.status)
    }
    return response.body
  }

  private suspend fun freshSession(): OlxSession {
    val session = store.load() ?: throw OlxSessionExpiredException("Sign in to OLX first.")
    if (session.expiresAtEpochMillis > System.currentTimeMillis() + REFRESH_EARLY_MILLIS) {
      return session
    }
    return refreshMutex.withLock {
      val latest = store.load() ?: throw OlxSessionExpiredException("Sign in to OLX first.")
      refresh(latest, force = false)
    }
  }

  private suspend fun refresh(session: OlxSession, force: Boolean): OlxSession {
    if (!force &&
        session.expiresAtEpochMillis > System.currentTimeMillis() + REFRESH_EARLY_MILLIS) {
      return session
    }
    val tokens =
        try {
          requestTokens(
              linkedMapOf(
                  "grant_type" to "refresh_token",
                  "client_id" to OlxAuthProtocol.CLIENT_ID,
                  "refresh_token" to session.refreshToken,
              ))
        } catch (error: OlxSessionExpiredException) {
          store.clearSession()
          throw error
        }
    return createSession(tokens, session).also(store::save)
  }

  private suspend fun createSession(tokens: OlxTokenDto, previous: OlxSession?): OlxSession {
    val refreshToken =
        tokens.refreshToken?.takeIf(String::isNotBlank)
            ?: previous?.refreshToken
            ?: throw OlxSessionExpiredException("OLX did not return a refresh token.")
    val profile = fetchProfile(tokens.accessToken)
    val accountId = profile.uuid ?: profile.id?.toString() ?: previous?.accountId
    if (accountId.isNullOrBlank()) throw OlxException("OLX did not identify the signed-in account.")
    val displayName =
        profile.name?.takeIf(String::isNotBlank)
            ?: profile.email?.takeIf(String::isNotBlank)
            ?: previous?.displayName
            ?: accountId
    return OlxSession(
        accessToken = tokens.accessToken,
        refreshToken = refreshToken,
        idToken = tokens.idToken?.takeIf(String::isNotBlank) ?: previous?.idToken,
        expiresAtEpochMillis = System.currentTimeMillis() + tokens.expiresIn * 1000L,
        accountId = accountId,
        displayName = displayName,
    )
  }

  private suspend fun fetchProfile(accessToken: String): OlxProfileDto {
    val response = execute(apiRequest(profileUrl, accessToken))
    if (response.status !in 200..299) {
      throw OlxException("Could not load the OLX account (HTTP ${response.status}).", response.status)
    }
    return json.decodeFromString<OlxProfileResponse>(response.body).data
  }

  private suspend fun requestTokens(parameters: Map<String, String>): OlxTokenDto {
    val body = FormBody.Builder().apply { parameters.forEach(::add) }.build()
    val request =
        Request.Builder()
            .url(authUrl)
            .header("Accept", "application/json")
            .header("User-Agent", userAgent())
            .post(body)
            .build()
    val response = execute(request)
    if (response.status !in 200..299) {
      if (response.status == 400 || response.status == 401) {
        throw OlxSessionExpiredException(
            "OLX authentication failed (HTTP ${response.status}).", response.status)
      }
      throw OlxException("OLX authentication failed (HTTP ${response.status}).", response.status)
    }
    return json.decodeFromString(response.body)
  }

  private fun apiRequest(url: HttpUrl, session: OlxSession): Request =
      apiRequest(url, session.accessToken)

  private fun apiRequest(url: HttpUrl, accessToken: String): Request =
      Request.Builder()
          .url(url)
          .header("Accept", "application/json")
          .header("Accept-Language", "pl")
          .header("User-Agent", userAgent())
          .header("X-Device-Id", store.deviceId())
          .header("Version", "v1.20")
          .header("X-Platform-Type", "android")
          .header("Authorization", "Bearer $accessToken")
          .get()
          .build()

  private suspend fun execute(request: Request): HttpResult =
      withTimeout(REQUEST_TIMEOUT_MILLIS) {
        client.newCall(request).executeAsync().use { response ->
          HttpResult(response.code, response.body.string())
        }
      }

  private fun userAgent(): String {
    val version = Build.VERSION.RELEASE.let { if ('.' in it) it else "$it.0" }
    return "Android App Ver 5.172.0 (Android $version;)"
  }

  private data class HttpResult(val status: Int, val body: String)

  private companion object {
    const val PAGE_SIZE = 20
    const val REFRESH_EARLY_MILLIS = 5 * 60 * 1000L
    const val REQUEST_TIMEOUT_MILLIS = 35_000L
    val refreshMutex = Mutex()
  }
}
