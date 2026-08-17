// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.api

private val urlSubstringRegex = """https?://\S+""".toRegex()

data class TrackingUrlMatch(
    val service: Service,
    val trackingId: String,
    val postalCode: String? = null,
)

// Extracts the first http(s) URL substring out of raw shared text (e.g. an
// ACTION_SEND EXTRA_TEXT payload that may contain a title/sentence around
// the link), trimming common trailing punctuation picked up by the regex.
fun extractFirstUrl(rawText: String): String? =
    urlSubstringRegex.find(rawText)?.value?.trimEnd('.', ',', ')', ']', '>', '"', '\'')

// Matches a full URL string against every in-scope provider's trackingUrlPatterns.
fun parseTrackingUrl(url: String): TrackingUrlMatch? {
  for (service in serviceOptions) {
    val backend = getDeliveryService(service) ?: continue
    for (pattern in backend.trackingUrlPatterns) {
      val trackingId =
          pattern.urlRegex.find(url)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
              ?: continue
      val postalCode =
          pattern.postalCodeRegex?.find(url)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
      return TrackingUrlMatch(service, trackingId, postalCode)
    }
  }
  return null
}

// Convenience wrapper for ACTION_SEND: extracts a URL from raw text first.
fun parseSharedText(rawText: String): TrackingUrlMatch? =
    extractFirstUrl(rawText)?.let { parseTrackingUrl(it) }
