// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.allegro

import dev.itsvic.parceltracker.api.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    assertEquals(Status.InTransit, AllegroParser.statusToAppStatus(details.status))
  }

  @Test
  fun parsesMultiboxPickupStatus() {
    val packages =
        AllegroParser.parsePackages(
            mapOf(
                "id" to "ALLEGRO:A00TEST002",
                "childComponents" to
                    listOf(
                        mapOf("text" to "Odbierz przesyłki"),
                        mapOf("text" to "multibox"),
                        mapOf("text" to "A00TEST002"),
                        mapOf("text" to "1 z 2 przesyłek"),
                        mapOf("text" to "Sample multibox product"),
                        mapOf("text" to "ODBIERZ Z MULTIBOXA"),
                    ),
                "actions" to
                    listOf(
                        mapOf(
                            "trigger" to "click",
                            "action" to
                                mapOf(
                                    "topic" to "share",
                                    "payload" to
                                        mapOf(
                                            "content" to
                                                "Odbierz za mnie przesyłkę z Multiboxa do czw. 13 sie 14:46 z Allegro One Box - TEST01, Example Street 1, Example City. Nr odbiorcy: 000 000 000. Kod odbioru: 111 222. Nr przesyłki A00TEST002.",
                                            "contentType" to "text"))),
                    ),
            ))

    assertEquals(1, packages.size)
    assertEquals("Odbierz przesyłki", packages.single().status)
    assertTrue(packages.single().readyForPickup)
    assertEquals("111 222", packages.single().pickupCode)
    assertEquals("000 000 000", packages.single().pickupPhoneNumber)
    assertEquals(
        Status.AwaitingPickup,
        AllegroParser.statusToAppStatus(packages.single().status, packages.single().readyForPickup),
    )
  }

  @Test
  fun parsesPickupCredentialsFromSingleBoxCardSharePayload() {
    val packages =
        AllegroParser.parsePackages(
            mapOf(
                "id" to "ALLEGRO:A00TEST003",
                "childComponents" to
                    listOf(
                        mapOf("text" to "Odbierz przesyłkę"),
                        mapOf("text" to "A00TEST003"),
                        mapOf("text" to "Sample product"),
                        mapOf("text" to "ODBIERZ"),
                    ),
                "actions" to
                    listOf(
                        mapOf(
                            "trigger" to "click",
                            "action" to
                                mapOf(
                                    "topic" to "share",
                                    "payload" to
                                        mapOf(
                                            "content" to
                                                "Odbierz za mnie przesyłkę do czw. 13 sie 14:14 z Allegro One Box - TEST01, Example Street 1, Example City. Nr odbiorcy: 000 000 000. Kod odbioru: 333 444. Nr przesyłki A00TEST003.",
                                            "contentType" to "text"))),
                    ),
            ))

    assertEquals(1, packages.size)
    assertEquals("333 444", packages.single().pickupCode)
    assertEquals("000 000 000", packages.single().pickupPhoneNumber)
    assertTrue(packages.single().readyForPickup)
  }

  @Test
  fun preservesPickupCredentialsWithoutSharePayload() {
    val packages =
        AllegroParser.parsePackages(
            mapOf(
                "id" to "ALLEGRO:A00TEST004",
                "childComponents" to
                    listOf(
                        mapOf("text" to "W drodze"),
                        mapOf("text" to "A00TEST004"),
                        mapOf("text" to "Sample product"),
                    ),
            ))

    assertEquals(1, packages.size)
    assertEquals("", packages.single().pickupCode)
    assertEquals("", packages.single().pickupPhoneNumber)
    assertFalse(packages.single().readyForPickup)
  }

  @Test
  fun parsesMultiboxGroupMarkers() {
    val packages =
        AllegroParser.parsePackages(
            mapOf(
                "id" to "ALLEGRO:A00GROUP001",
                "childComponents" to
                    listOf(
                        mapOf("text" to "Odbierz przesyłki"),
                        mapOf("text" to "multibox"),
                        mapOf("text" to "A00GROUP001"),
                        mapOf("text" to "1 z 2 przesyłek"),
                        mapOf("text" to "Sample multibox product"),
                        mapOf("text" to "ODBIERZ Z MULTIBOXA"),
                        mapOf("text" to "POKAŻ WSZYSTKIE PRZESYŁKI"),
                    ),
                "actions" to
                    listOf(
                        mapOf(
                            "trigger" to "click",
                            "action" to
                                mapOf(
                                    "url" to
                                        "allegro-mbox://render?boxId=ALLEGRO_A00GROUP001&route=https%3A%2F%2Fallegro.pl%2F",
                                    "type" to "open"))),
            ))

    assertEquals(1, packages.size)
    assertEquals("ALLEGRO_A00GROUP001", packages.single().multiboxGroupId)
    assertEquals("1 z 2 przesyłek", packages.single().multiboxIndex)
  }

  @Test
  fun ignoresBoxIdFromAnalyticsPayloads() {
    val packages =
        AllegroParser.parsePackages(
            mapOf(
                "id" to "ALLEGRO:A00TEST005",
                "childComponents" to
                    listOf(
                        mapOf("text" to "Odbierz przesyłkę"),
                        mapOf("text" to "A00TEST005"),
                        mapOf("text" to "Sample product"),
                        mapOf("text" to "ODBIERZ"),
                    ),
                "actions" to
                    listOf(
                        mapOf(
                            "action" to
                                mapOf(
                                    "events" to
                                        listOf(
                                            mapOf(
                                                "customParams" to
                                                    mapOf(
                                                        "orderId" to
                                                            "00000000-0000-0000-0000-000000000001",
                                                        "_opbox" to
                                                            mapOf(
                                                                "boxId" to
                                                                    "test-box-id"))))))),
            ))

    assertEquals(1, packages.size)
    assertEquals("", packages.single().multiboxGroupId)
  }
}
