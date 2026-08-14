// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.olx

import android.content.Context
import com.chuckerteam.chucker.api.ChuckerInterceptor
import okhttp3.Interceptor

internal fun createOlxDebugInterceptor(context: Context): Interceptor {
  val chucker =
      ChuckerInterceptor.Builder(context)
          .maxContentLength(5_000_000L)
          .redactHeaders("Authorization", "Cookie", "Set-Cookie", "X-Device-Id")
          .alwaysReadResponseBody(true)
          .build()
  return Interceptor { chain ->
    val request = chain.request()
    if (request.url.encodedPath.startsWith("/order-overview/")) {
      chucker.intercept(chain)
    } else {
      chain.proceed(request)
    }
  }
}
