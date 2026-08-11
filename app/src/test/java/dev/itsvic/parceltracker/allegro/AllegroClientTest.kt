// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.allegro

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AllegroClientTest {
  private lateinit var server: MockWebServer

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()
  }

  @After
  fun tearDown() {
    server.close()
  }

  @Test
  fun performsAndroidLoginFlow() = runBlocking {
    server.enqueue(
        MockResponse.Builder()
            .code(204)
            .addHeader("Set-Cookie", "wdctx=wd; Path=/")
            .addHeader("Set-Cookie", "datadome=dd; Path=/")
            .build())
    server.enqueue(
        MockResponse.Builder().code(200).addHeader("Set-Cookie", "QXLSESSID=qx; Path=/").build())
    server.enqueue(
        MockResponse.Builder()
            .code(200)
            .body(
                """{"accessToken":"token","accessTokenExpiration":"2026-08-11T00:00:00Z","username":"testuser"}""")
            .build())
    val client = AllegroClient(allegroUrl = server.url("/"), edgeUrl = server.url("/"))

    val session = client.login("testuser", "testpass")

    assertEquals("token", session.accessToken)
    assertEquals("wd", session.wdctx)
    assertEquals("dd", session.datadome)
    assertEquals("qx", session.qxlsessid)
    val check = server.takeRequest(1, TimeUnit.SECONDS)!!
    val initialization = server.takeRequest(1, TimeUnit.SECONDS)!!
    val verification = server.takeRequest(1, TimeUnit.SECONDS)!!
    assertEquals("HEAD", check.method)
    assertEquals("/client-check", check.url.encodedPath)
    assertEquals("/authentication/initialization/mobile", initialization.url.encodedPath)
    assertEquals("/authentication/credentials/mobile/verification", verification.url.encodedPath)
    assertEquals("{\"login\":\"testuser\",\"password\":\"testpass\"}", verification.body!!.utf8())
    assertTrue(verification.headers["Cookie"]!!.contains("QXLSESSID=qx"))
  }

  @Test
  fun fetchesDashboardAndPickupDetails() = runBlocking {
    server.enqueue(
        MockResponse.Builder()
            .addHeader("Content-Encoding", "gzip")
            .body(gzip("""{"total":1,"parcelsForPickup":1}"""))
            .build())
    server.enqueue(
        MockResponse.Builder()
            .body(
                """{"packages":[{"id":"A00TEST001","title":"Sample item","status":"Gotowa do odbioru","carrier":"Allegro One","carrierId":"ALLEGRO","trackingNumber":"A00TEST001"}]}""")
            .build())
    server.enqueue(
        MockResponse.Builder()
            .body(
                """{"waybill":"A00TEST001","carrierId":"ALLEGRO","carrierName":"Allegro One","formattedCode":"000 000","formattedPhoneNumber":"+48 000 000 000","qrCode":"test-qr-payload"}""")
            .build())
    val client =
        AllegroClient(
            session =
                AllegroSession(
                    username = "testuser",
                    accessToken = "token",
                    wdctx = "wd",
                    datadome = "dd",
                ),
            allegroUrl = server.url("/"),
            edgeUrl = server.url("/"),
        )

    val packages = client.fetchDashboard()
    val pickup = client.fetchPickupDetails("ALLEGRO", "A00TEST001")

    assertEquals(1, packages.size)
    assertTrue(packages.single().readyForPickup)
    assertEquals("000 000", pickup.code)
    assertEquals("+48 000 000 000", pickup.phoneNumber)
    assertEquals("test-qr-payload", pickup.qrPayload)
    val summaryRequest = server.takeRequest(1, TimeUnit.SECONDS)!!
    val renderRequest = server.takeRequest(1, TimeUnit.SECONDS)!!
    val pickupRequest = server.takeRequest(1, TimeUnit.SECONDS)!!
    assertEquals("application/vnd.allegro.internal.v2+json", summaryRequest.headers["Accept"])
    assertEquals("gzip", summaryRequest.headers["Accept-Encoding"])
    assertEquals("/mobile/render", renderRequest.url.encodedPath)
    assertEquals(
        "/packages/carrier/ALLEGRO/waybill/A00TEST001/pickup-details",
        pickupRequest.url.encodedPath,
    )
    assertEquals("application/vnd.allegro.beta.v1+json", pickupRequest.headers["Accept"])
  }

  private fun gzip(value: String) =
      Buffer().apply { GzipSink(this).buffer().use { sink -> sink.writeUtf8(value) } }
}
