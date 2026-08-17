import dev.itsvic.parceltracker.api.Service
import dev.itsvic.parceltracker.api.TrackingUrlMatch
import dev.itsvic.parceltracker.api.extractFirstUrl
import dev.itsvic.parceltracker.api.parseSharedText
import dev.itsvic.parceltracker.api.parseTrackingUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackingUrlParserTest {
  @Test
  fun dhlParcelNl_urlParsesToServiceAndTrackingId() {
    assertEquals(
        TrackingUrlMatch(Service.DHL, "JVGL00000000000000000000"),
        parseTrackingUrl("https://my.dhlparcel.nl/go-track-trace?pid=JVGL00000000000000000000"),
    )
  }

  @Test
  fun ups_urlParsesToServiceAndTrackingId() {
    assertEquals(
        TrackingUrlMatch(Service.UPS, "1Z999AA10123456784"),
        parseTrackingUrl("https://www.ups.com/track?loc=en_US&tracknum=1Z999AA10123456784"),
    )
  }

  @Test
  fun gls_urlParsesToServiceAndTrackingId() {
    assertEquals(
        TrackingUrlMatch(Service.GLS, "1234567890"),
        parseTrackingUrl("https://gls-group.eu/EU/en/parcel-tracking?match=1234567890"),
    )
  }

  @Test
  fun gls_otherCountryTldDoesNotMatch() {
    // gls-info.nl is confirmed; other country TLDs are deliberately not supported
    // since that generalization was never verified.
    assertNull(
        parseTrackingUrl(
            "https://www.gls-info.de/tracking/ttlink?parcelNo=1234567890&zipCode=12345"))
  }

  @Test
  fun glsNetherlands_urlParsesToServiceTrackingIdAndPostalCode() {
    assertEquals(
        TrackingUrlMatch(Service.GLS_NETHERLANDS, "00000000000000", "1234AB"),
        parseTrackingUrl(
            "https://www.gls-info.nl/tracking/ttlink?parcelNo=00000000000000&zipCode=1234AB&lang=NL"),
    )
  }

  @Test
  fun inpost_urlParsesToServiceAndTrackingId() {
    assertEquals(
        TrackingUrlMatch(Service.INPOST, "123456789012345678901234"),
        parseTrackingUrl("https://inpost.pl/en/tracking?number=123456789012345678901234"),
    )
  }

  @Test
  fun postNL_pathUrlParsesToServiceTrackingIdAndPostalCode() {
    assertEquals(
        TrackingUrlMatch(Service.POST_NL, "3SABCD1234567", "1234AB"),
        parseTrackingUrl("https://jouw.postnl.nl/track-and-trace/3SABCD1234567-NL-1234AB"),
    )
  }

  @Test
  fun polishPost_urlParsesToServiceAndTrackingId() {
    assertEquals(
        TrackingUrlMatch(Service.POLISH_POST, "PX1234567890"),
        parseTrackingUrl("https://emonitoring.poczta-polska.pl/?numer=PX1234567890"),
    )
  }

  @Test
  fun samedayRomania_fragmentUrlParsesToServiceAndTrackingId() {
    assertEquals(
        TrackingUrlMatch(Service.SAMEDAY_RO, "1234567890123"),
        parseTrackingUrl("https://sameday.ro/#awb=1234567890123"),
    )
  }

  @Test
  fun samedayRomania_statusPageUrlParsesToServiceAndTrackingId() {
    assertEquals(
        TrackingUrlMatch(Service.SAMEDAY_RO, "1234567890123"),
        parseTrackingUrl("https://sameday.ro/status-colet/?awb=1234567890123"),
    )
  }

  @Test
  fun samedayHungary_fragmentUrlParsesToServiceAndTrackingId() {
    assertEquals(
        TrackingUrlMatch(Service.SAMEDAY_HU, "1234567890123"),
        parseTrackingUrl("https://sameday.hu/#awb=1234567890123"),
    )
  }

  @Test
  fun samedayBulgaria_fragmentUrlParsesToServiceAndTrackingId() {
    assertEquals(
        TrackingUrlMatch(Service.SAMEDAY_BG, "1234567890123"),
        parseTrackingUrl("https://sameday.bg/#awb=1234567890123"),
    )
  }

  @Test
  fun samedayBulgaria_statusPageUrlParsesToServiceAndTrackingId() {
    assertEquals(
        TrackingUrlMatch(Service.SAMEDAY_BG, "1234567890123"),
        parseTrackingUrl("https://sameday.bg/status-na-pratkata/?awb=1234567890123"),
    )
  }

  // Providers without a confirmed tracking URL format have no trackingUrlPatterns
  // and always fall through to null (which the UI treats as "open a blank Add
  // Parcel form"). A couple of generic cases cover that fallback path.

  @Test
  fun unknownHost_returnsNull() {
    assertNull(parseTrackingUrl("https://example.com/track?tracking-id=1234567890"))
  }

  @Test
  fun knownHostWrongParam_returnsNull() {
    assertNull(parseTrackingUrl("https://gls-group.eu/EU/en/parcel-tracking?foo=1234567890"))
  }

  @Test
  fun postNord_obfuscatedWidgetLink_returnsNull() {
    // Regression test: PostNord's real, documented shareable widget link uses an
    // opaque id combining an API key and the shipment ID - it must never be
    // mistaken for a real tracking number, even though we don't parse PostNord.
    assertNull(
        parseTrackingUrl(
            "https://tracking.postnord.com/?id=06e097dd:6669:4a88:5159:bafa:40abc79c10ab1bf3:070SE"))
  }

  @Test
  fun extractFirstUrl_pullsUrlOutOfSurroundingText() {
    assertEquals(
        "https://example.com/track?id=123",
        extractFirstUrl("Track your parcel: https://example.com/track?id=123 Thanks!"),
    )
  }

  @Test
  fun extractFirstUrl_returnsNullForPlainText() {
    assertNull(extractFirstUrl("just some plain text, no link here"))
  }

  @Test
  fun parseSharedText_combinesExtractionAndParsing() {
    assertEquals(
        TrackingUrlMatch(Service.UPS, "1Z999AA10123456784"),
        parseSharedText(
            "Your UPS parcel is on its way: https://www.ups.com/track?loc=en_US&tracknum=1Z999AA10123456784"),
    )
  }

  @Test
  fun parseSharedText_returnsNullWhenNoUrlPresent() {
    assertNull(parseSharedText("no link in this message"))
  }
}
