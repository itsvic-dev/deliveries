import dev.itsvic.parceltracker.api.AllegroOneBoxDeliveryService
import dev.itsvic.parceltracker.api.AnPostDeliveryService
import dev.itsvic.parceltracker.api.DpdPlDeliveryService
import dev.itsvic.parceltracker.api.FPXDeliveryService
import dev.itsvic.parceltracker.api.InPostDeliveryService
import dev.itsvic.parceltracker.api.MagyarPostaDeliveryService
import dev.itsvic.parceltracker.api.PacketaDeliveryService
import dev.itsvic.parceltracker.api.PolishPostDeliveryService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatValidationTest {
  @Test
  fun polishPost_PocztexFormatReturnsTrue() {
    assertTrue(PolishPostDeliveryService.acceptsFormat("PX1234567890"))
  }

  @Test
  fun polishPost_EMSFormatReturnsTrue() {
    assertTrue(PolishPostDeliveryService.acceptsFormat("EX123456789PL"))
  }

  @Test
  fun polishPost_NonsenseReturnsFalse() {
    assertFalse(PolishPostDeliveryService.acceptsFormat("fsjkdkjjksd"))
  }

  @Test
  fun hungarianPost_EMSFormatReturnsTrue() {
    assertTrue(MagyarPostaDeliveryService.acceptsFormat("EX123456789PL"))
  }

  @Test
  fun hungarianPost_GovFormatReturnsTrue() {
    assertTrue(MagyarPostaDeliveryService.acceptsFormat("RL12345678901234"))
  }

  @Test
  fun hungarianPost_WeirdFormatReturnsTrue() {
    assertTrue(MagyarPostaDeliveryService.acceptsFormat("JJH12AAAAAPL12345678"))
  }

  @Test
  fun hungarianPost_ParcelLockerFormatReturnsTrue() {
    assertTrue(MagyarPostaDeliveryService.acceptsFormat("PNTM1234567890123456789012"))
  }

  @Test
  fun hungarianPost_NonsenseReturnsFalse() {
    assertFalse(MagyarPostaDeliveryService.acceptsFormat("fsjkdkjjksd"))
  }

  @Test
  fun packeta_CanonicalFormat_ReturnsTrue() {
    assertTrue(PacketaDeliveryService.acceptsFormat("Z 123 4567 890"))
  }

  @Test
  fun packeta_CanonicalFormatNoSpaces_ReturnsTrue() {
    assertTrue(PacketaDeliveryService.acceptsFormat("Z1234567890"))
  }

  @Test
  fun anPostFormatReturnsTrue() {
    assertTrue(AnPostDeliveryService.acceptsFormat("Ay123456789Ie"))
  }

  @Test
  fun anPostFormatReturnsFalse() {
    assertFalse(AnPostDeliveryService.acceptsFormat("AY12345678HA"))
  }

  @Test
  fun allegroOneBox_ReturnsTrue() {
    assertTrue(AllegroOneBoxDeliveryService.acceptsFormat("A0100AA0A0"))
  }

  @Test
  fun allegroOneBox_ReturnsFalse() {
    assertFalse(AllegroOneBoxDeliveryService.acceptsFormat("aaa"))
    assertFalse(AllegroOneBoxDeliveryService.acceptsFormat("A02AAAAAA"))
    assertFalse(AllegroOneBoxDeliveryService.acceptsFormat("A0000AA0Aa"))
    assertFalse(AllegroOneBoxDeliveryService.acceptsFormat("A 0000AA0A"))
    assertFalse(AllegroOneBoxDeliveryService.acceptsFormat("AAAAAAAAAAA"))
    assertFalse(AllegroOneBoxDeliveryService.acceptsFormat("B000000000"))
  }

  @Test
  fun inpost_NumberFormatReturnsTrue() {
    assertTrue(InPostDeliveryService.acceptsFormat("000000000000000000000000"))
  }

  @Test
  fun inpost_NumberFormatReturnsFalse() {
    assertFalse(InPostDeliveryService.acceptsFormat("A00000000000000000000000"))
  }

  @Test
  fun fpx_ReturnsTrue() {
    assertTrue(FPXDeliveryService.acceptsFormat("4PX00000000000000CN"))
  }

  @Test
  fun fpx_ReturnsFalse() {
    assertFalse(FPXDeliveryService.acceptsFormat("eawifjsadmf"))
    assertFalse(FPXDeliveryService.acceptsFormat("4PX00a00000000000CN"))
  }

  @Test
  fun dpdPoland_NumberFormatsReturnTrue() {
    assertTrue(DpdPlDeliveryService.acceptsFormat("12345678901234"))
    assertTrue(DpdPlDeliveryService.acceptsFormat("1234567890123U"))
    assertTrue(DpdPlDeliveryService.acceptsFormat("1234567890123u"))
    assertTrue(DpdPlDeliveryService.acceptsFormat("1234567890123W"))
  }

  @Test
  fun dpdPoland_InvalidFormatsReturnFalse() {
    assertFalse(DpdPlDeliveryService.acceptsFormat("1234567890123"))
    assertFalse(DpdPlDeliveryService.acceptsFormat("123456789012345"))
    assertFalse(DpdPlDeliveryService.acceptsFormat("123456789012AB"))
    assertFalse(DpdPlDeliveryService.acceptsFormat(" 12345678901234"))
  }
}
