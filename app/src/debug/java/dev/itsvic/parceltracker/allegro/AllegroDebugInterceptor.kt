// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.allegro

import android.content.Context
import com.chuckerteam.chucker.api.ChuckerInterceptor
import okhttp3.Interceptor

internal fun createAllegroDebugInterceptor(context: Context): Interceptor {
  val chucker =
      ChuckerInterceptor.Builder(context)
          .maxContentLength(5_000_000L)
          .redactHeaders("Authorization", "Cookie", "Set-Cookie", "x-wdctx")
          .alwaysReadResponseBody(true)
          .createShortcut(true)
          .build()

  return Interceptor { chain ->
    val request = chain.request()
    val isPackageSync =
        request.url.encodedPath == "/packages/summary" ||
            (request.url.encodedPath == "/mobile/render" &&
                request.url.queryParameter("route")?.contains("szczegoly-dostawy") != true)
    if (isPackageSync) chucker.intercept(chain) else chain.proceed(request)
  }
}
