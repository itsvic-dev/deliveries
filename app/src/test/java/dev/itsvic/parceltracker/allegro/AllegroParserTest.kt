// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.allegro

import org.junit.Assert.assertEquals
import org.junit.Test

class AllegroParserTest {
  @Test
  fun parsesCurrentAndroidCardsWithoutActionLabels() {
    val packages =
        AllegroParser.parsePackages(
            mapOf(
                "content" to
                    mapOf(
                        "component" to
                            mapOf(
                                "childComponents" to
                                    listOf(
                                        mapOf(
                                            "id" to "ALLEGRO:A00TEST001",
                                            "childComponents" to
                                                listOf(
                                                    mapOf(
                                                        "accessibility" to
                                                            mapOf("label" to "Dodatkowe opcje"),
                                                        "actions" to
                                                            listOf(
                                                                mapOf(
                                                                    "action" to
                                                                        mapOf(
                                                                            "waybill" to
                                                                                "A00TEST001")))),
                                                    mapOf(
                                                        "childComponents" to
                                                            listOf(
                                                                mapOf(
                                                                    "text" to
                                                                        "Przesyłka oczekuje na nadanie"))),
                                                    mapOf(
                                                        "childComponents" to
                                                            listOf(
                                                                mapOf(
                                                                    "text" to
                                                                        "Przewidywana dostawa: jutro"))),
                                                    mapOf(
                                                        "childComponents" to
                                                            listOf(
                                                                mapOf(
                                                                    "text" to
                                                                        "Allegro One Box - TST01"))),
                                                    mapOf(
                                                        "childComponents" to
                                                            listOf(mapOf("text" to "A00TEST001"))),
                                                    mapOf(
                                                        "childComponents" to
                                                            listOf(
                                                                mapOf(
                                                                    "text" to
                                                                        "Sample product name"))),
                                                    mapOf(
                                                        "childComponents" to
                                                            listOf(
                                                                mapOf(
                                                                    "text" to
                                                                        "SZCZEGÓŁY DOSTAWY"))),
                                                )),
                                    )))))

    assertEquals(1, packages.size)
    assertEquals("Sample product name", packages.single().title)
    assertEquals("ALLEGRO", packages.single().carrierId)
    assertEquals("Allegro One", packages.single().carrier)
    assertEquals("Allegro One Box - TST01", packages.single().pickupPoint)
  }

  @Test
  fun parsesTrackingHistoryFromDetails() {
    val original =
        AllegroPackage(
            packageId = "pkg-test-001",
            title = "Sample item",
            status = "W drodze",
            carrier = "InPost",
            trackingNumber = "520000000000000000000000",
        )
    val details =
        AllegroParser.parsePackageDetails(
            mapOf(
                "content" to
                    mapOf(
                        "component" to
                            mapOf(
                                "childComponents" to
                                    listOf(
                                        mapOf("text" to "Przesyłka dzisiaj w punkcie"),
                                        mapOf("text" to "Historia przesyłki"),
                                        mapOf("text" to "Przekazana do doręczenia"),
                                        mapOf("text" to "10 sie 2026, 07:49"),
                                    )))),
            original,
        )

    assertEquals("Przesyłka dzisiaj w punkcie", details.status)
    assertEquals("Przekazana do doręczenia", details.history.single().status)
  }
}
