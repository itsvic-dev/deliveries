import dev.itsvic.parceltracker.api.localDateFromISO
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class DateParsingTest {
  private fun expectedInZone(instant: String): LocalDateTime =
      ZonedDateTime.parse(instant).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()

  @Test
  fun localDateFromISO_offsetlessIsTakenAsLocalTime() {
    assertEquals(LocalDateTime.of(2026, 8, 17, 7, 47, 32), localDateFromISO("2026-08-17T07:47:32"))
  }

  @Test
  fun localDateFromISO_fractionalSecondsAreAccepted() {
    assertEquals(
        LocalDateTime.of(2026, 8, 14, 11, 52, 1, 878_000_000),
        localDateFromISO("2026-08-14T11:52:01.878"),
    )
  }

  // GLS Netherlands returns the delivery ETA with an offset while every other timestamp in the
  // same response has none.
  @Test
  fun localDateFromISO_offsetIsConvertedToDeviceZone() {
    assertEquals(
        expectedInZone("2026-08-17T11:26:00+02:00"),
        localDateFromISO("2026-08-17T11:26:00+02:00"),
    )
  }

  @Test
  fun localDateFromISO_zuluIsConvertedToDeviceZone() {
    assertEquals(expectedInZone("2026-08-17T00:00:00Z"), localDateFromISO("2026-08-17T00:00:00Z"))
  }

  @Test
  fun localDateFromISO_zoneRegionIsConvertedToDeviceZone() {
    val default = TimeZone.getDefault()
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
      assertEquals(
          LocalDateTime.of(2026, 8, 17, 9, 26, 0),
          localDateFromISO("2026-08-17T11:26:00+02:00[Europe/Amsterdam]"),
      )
    } finally {
      TimeZone.setDefault(default)
    }
  }
}
