// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.allegro

import android.content.Context
import com.chuckerteam.chucker.api.Chucker
import com.chuckerteam.chucker.api.ChuckerInterceptor
import okhttp3.Interceptor

internal fun openAllegroNetworkInspector(context: Context) {
  context.startActivity(Chucker.getLaunchIntent(context))
}

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
            request.url.encodedPath.startsWith("/packages/carrier/") ||
            request.url.encodedPath == "/mobile/render"
    if (isPackageSync) chucker.intercept(chain) else chain.proceed(request)
  }
}
