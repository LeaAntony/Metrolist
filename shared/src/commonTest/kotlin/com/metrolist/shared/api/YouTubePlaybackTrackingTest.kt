package com.metrolist.shared.api

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.extraction.PlayerConfig
import com.metrolist.innertubex.extraction.YtConfigParser
import com.metrolist.innertubex.models.YouTubeLocale
import com.metrolist.shared.settings.Settings
import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.content.TextContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YouTubePlaybackTrackingTest {
    @Test
    fun reportingRequiresLoginSyncAndUnpausedHistoryForEveryPing() =
        runBlocking {
            Settings.replaceSharedForTest(Settings(MapSettings()))
            val engine = MockEngine { error("Disabled reporting must not contact YouTube") }
            val client = HttpClient(engine)
            val tube = InnerTube(client)
            try {
                val tracking = YouTubePlaybackTracking(tube)
                for ((cookie, sync, paused) in listOf(
                    Triple("", true, false),
                    Triple("PREF=theme=3", true, false),
                    Triple("SAPISID=account", false, false),
                    Triple("SAPISID=account", true, true),
                )) {
                    tube.replaceSession(cookie, "visitor", "account", "0", true)
                    Settings.shared.ytmSync = sync
                    Settings.shared.pauseListenHistory = paused
                    val session = tracking.playbackTrackingSession()
                    assertEquals(false, tracking.isPlaybackReportingEnabled())
                    assertEquals(false, tracking.registerPlayback("https://s.youtube.com/api/stats/playback", "nonce", "0", null, session))
                    assertEquals(
                        false,
                        tracking.registerWatchtime(
                            "https://s.youtube.com/api/stats/watchtime",
                            "nonce",
                            "0",
                            "1",
                            "1",
                            "1",
                            true,
                            null,
                            session,
                        ),
                    )
                }
                assertEquals(0, engine.requestHistory.size)
            } finally {
                tube.close()
                client.close()
                Settings.replaceSharedForTest(null)
            }
        }

    @Test
    fun trackingUrlValidationPreventsRequestsOutsideAllowedHttpsEndpoints() =
        runBlocking {
            Settings.replaceSharedForTest(Settings(MapSettings()).apply { pauseListenHistory = false })
            val engine = MockEngine { respond("") }
            val client = HttpClient(engine)
            val tube = InnerTube(client, retryDelay = {}).apply { cookie = "SAPISID=original" }
            try {
                val tracking = YouTubePlaybackTracking(tube)
                val session = tracking.playbackTrackingSession()
                val invalidUrls =
                    listOf(
                        "http://music.youtube.com/api/stats/playback",
                        "https://music.youtube.com.evil.example/api/stats/playback",
                        "https://evil-music.youtube.com/api/stats/playback",
                        "https://youtube.com/api/stats/playback",
                        "https://user@music.youtube.com/api/stats/playback",
                        "https://user:password@s.youtube.com/api/stats/playback",
                        "https://music.youtube.com:444/api/stats/playback",
                        "https://music.youtube.com/api/stats/other",
                        "https://music.youtube.com/api/stats/playback/extra",
                    )
                for (url in invalidUrls) {
                    assertEquals(false, tracking.registerPlayback(url, "nonce", "0", null, session), url)
                    assertEquals(false, tracking.registerWatchtime(url, "nonce", "0", "1", "1", "1", false, null, session), url)
                    assertEquals(0, engine.requestHistory.size, url)
                }
                for (host in listOf("music.youtube.com", "s.youtube.com")) {
                    assertEquals(true, tracking.registerPlayback("https://$host/api/stats/playback", "nonce", "0", null, session))
                    assertEquals(
                        true,
                        tracking.registerWatchtime("https://$host/api/stats/watchtime", "nonce", "0", "1", "1", "1", false, null, session),
                    )
                }
                assertEquals(4, engine.requestHistory.size)
                engine.requestHistory.forEach { assertNotNull(it.headers[HttpHeaders.Authorization]) }
            } finally {
                tube.close()
                client.close()
                Settings.replaceSharedForTest(null)
            }
        }

    @Test
    fun playerRequestIncludesSignatureTimestampRefererSessionScopeAndAtrUrl() =
        runBlocking {
            Settings.replaceSharedForTest(Settings(MapSettings()).apply { pauseListenHistory = false })
            val shellHtml =
                """
                <script>ytcfg.set({
                  "LOGGED_IN": true,
                  "INNERTUBE_CLIENT_VERSION": "1.20261115.01.00",
                  "DELEGATED_SESSION_ID": "brand-channel-42",
                  "DATASYNC_ID": "brand-channel-42||personal-gaia-99",
                  "VISITOR_DATA": "Cg_session_visitor",
                  "SESSION_INDEX": "1"
                });</script>
                """.trimIndent()
            val engine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/" -> {
                            respond(shellHtml, headers = headersOf(HttpHeaders.ContentType, "text/html"))
                        }

                        "/youtubei/v1/player" -> {
                            respond(
                                """
                                {
                                  "playabilityStatus": { "status": "OK" },
                                  "playbackTracking": {
                                    "videostatsPlaybackUrl": { "baseUrl": "https://s.youtube.com/api/stats/playback?el=detailpage" },
                                    "videostatsWatchtimeUrl": { "baseUrl": "https://s.youtube.com/api/stats/watchtime?el=detailpage" },
                                    "atrUrl": {
                                      "baseUrl": "https://s.youtube.com/api/stats/atr?ver=2&c=WEB_REMIX&cver=1.20261115.01.00&atr_params=abc",
                                      "elapsedMediaTimeSeconds": 7
                                    }
                                  }
                                }
                                """.trimIndent(),
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }

                        else -> {
                            respond("", HttpStatusCode.NoContent)
                        }
                    }
                }
            val client = HttpClient(engine)
            val tube =
                InnerTube(client, retryDelay = {}).apply {
                    replaceSession("__Secure-3PAPISID=secret-sap", "", "brand-channel-42", "", true)
                    locale = YouTubeLocale(gl = "ID", hl = "id")
                }
            val fakeParser =
                object : YtConfigParser {
                    override suspend fun fetchConfig(
                        videoId: String,
                        useLoginCookies: Boolean,
                    ): PlayerConfig =
                        PlayerConfig(
                            playerUrl = "https://www.youtube.com/s/player/abc/base.js",
                            signatureTimestamp = 20456,
                            visitorData = "watch-visitor",
                            clientVersion = "2.20261115.00.00",
                            encryptedHostFlags = null,
                        )
                }
            try {
                val tracking =
                    YouTubePlaybackTracking(
                        innerTube = tube,
                        ytConfigParserProvider = { fakeParser },
                        fetchWebRemixSessionContext = true,
                    )
                val session = tracking.playbackTrackingSession()
                val resolved = tracking.getResolvedPlaybackTracking("dQw4w9WgXcQ", "cpn-123", session)
                assertNotNull(resolved)
                assertEquals(7L, resolved.atrAfterSeconds)
                assertTrue(resolved.atrUrl?.contains("/api/stats/atr") == true)

                assertEquals(
                    true,
                    tracking.registerPlayback(
                        playbackTrackingUrl = resolved.data.playbackUrl!!,
                        clientPlaybackNonce = "cpn-123",
                        currentMediaTime = "0.0",
                        format = 251,
                        playbackSession = session,
                    ),
                )
                assertEquals(
                    true,
                    tracking.registerAtr(
                        atrTrackingUrl = resolved.atrUrl,
                        clientPlaybackNonce = "cpn-123",
                        playbackSession = session,
                    ),
                )
                assertEquals(
                    true,
                    tracking.registerWatchtime(
                        watchtimeTrackingUrl = resolved.data.watchtimeUrl!!,
                        clientPlaybackNonce = "cpn-123",
                        startTimes = "0.0",
                        endTimes = "10.0",
                        currentMediaTime = "10.0",
                        realTime = "10.0",
                        final = false,
                        format = 251,
                        playbackSession = session,
                    ),
                )
                assertEquals(
                    true,
                    tracking.registerWatchtime(
                        watchtimeTrackingUrl = resolved.data.watchtimeUrl!!,
                        clientPlaybackNonce = "cpn-123",
                        startTimes = "10.0",
                        endTimes = "15.0",
                        currentMediaTime = "15.0",
                        realTime = "15.0",
                        final = true,
                        format = 251,
                        playbackSession = session,
                    ),
                )

                val playerRequest = engine.requestHistory.single { it.url.encodedPath == "/youtubei/v1/player" }
                val playerJson = Json.parseToJsonElement((playerRequest.body as TextContent).text).jsonObject
                val contentCtx =
                    playerJson["playbackContext"]!!
                        .jsonObject["contentPlaybackContext"]!!
                        .jsonObject
                assertEquals("https://music.youtube.com/watch?v=dQw4w9WgXcQ", contentCtx["referer"]?.jsonPrimitive?.content)
                assertEquals(20456, contentCtx["signatureTimestamp"]?.jsonPrimitive?.intOrNull)
                val clientCtx = playerJson["context"]!!.jsonObject["client"]!!.jsonObject
                assertEquals("1.20261115.01.00", clientCtx["clientVersion"]?.jsonPrimitive?.content)
                assertEquals("Cg_session_visitor", clientCtx["visitorData"]?.jsonPrimitive?.content)
                assertEquals("brand-channel-42", playerRequest.headers["X-Goog-PageId"])
                assertEquals("Cg_session_visitor", playerRequest.headers["X-Goog-Visitor-Id"])
                assertTrue(playerRequest.headers[HttpHeaders.Authorization]?.startsWith("SAPISIDHASH ") == true)

                val playbackPing = engine.requestHistory.single { it.url.encodedPath == "/api/stats/playback" }
                assertEquals("2", playbackPing.url.parameters["ver"])
                assertEquals("WEB_REMIX", playbackPing.url.parameters["c"])
                assertEquals("1.20261115.01.00", playbackPing.url.parameters["cver"])
                assertEquals("cpn-123", playbackPing.url.parameters["cpn"])
                assertEquals("UNIPLAYER", playbackPing.url.parameters["cplayer"])
                assertEquals("id", playbackPing.url.parameters["hl"])
                assertEquals("ID", playbackPing.url.parameters["cr"])
                assertEquals("playing", playbackPing.url.parameters["state"])
                assertNull(playbackPing.url.parameters["final"])
                assertEquals("brand-channel-42", playbackPing.headers["X-Goog-PageId"])
                assertTrue(playbackPing.headers[HttpHeaders.Authorization]?.startsWith("SAPISIDHASH ") == true)

                val atrPing = engine.requestHistory.single { it.url.encodedPath == "/api/stats/atr" }
                assertEquals("cpn-123", atrPing.url.parameters["cpn"])
                assertEquals("abc", atrPing.url.parameters["atr_params"])
                assertEquals("brand-channel-42", atrPing.headers["X-Goog-PageId"])

                val watchtimes = engine.requestHistory.filter { it.url.encodedPath == "/api/stats/watchtime" }
                assertEquals(2, watchtimes.size)
                assertEquals("playing", watchtimes[0].url.parameters["state"])
                assertNull(watchtimes[0].url.parameters["final"])
                assertEquals("paused", watchtimes[1].url.parameters["state"])
                assertEquals("1", watchtimes[1].url.parameters["final"])
            } finally {
                tube.close()
                client.close()
                Settings.replaceSharedForTest(null)
            }
        }

    @Test
    fun personalAccountOmitsDelegatedPageIdHeaderEvenWhenShellLoaded() =
        runBlocking {
            Settings.replaceSharedForTest(Settings(MapSettings()).apply { pauseListenHistory = false })
            val shellHtml =
                """
                <script>ytcfg.set({
                  "LOGGED_IN": true,
                  "INNERTUBE_CLIENT_VERSION": "1.20261115.01.00",
                  "DATASYNC_ID": "||personal-gaia-99",
                  "VISITOR_DATA": "Cg_personal_visitor",
                  "SESSION_INDEX": "0"
                });</script>
                """.trimIndent()
            val engine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/" -> respond(shellHtml, headers = headersOf(HttpHeaders.ContentType, "text/html"))
                        else -> respond("", HttpStatusCode.NoContent)
                    }
                }
            val client = HttpClient(engine)
            val tube =
                InnerTube(client, retryDelay = {}).apply {
                    replaceSession("SAPISID=token", "visitor", "personal-gaia-99", "0", true)
                }
            try {
                val tracking = YouTubePlaybackTracking(tube, fetchWebRemixSessionContext = true)
                val session = tracking.playbackTrackingSession()
                assertTrue(
                    tracking.registerPlayback(
                        playbackTrackingUrl = "https://s.youtube.com/api/stats/playback",
                        clientPlaybackNonce = "cpn-personal",
                        currentMediaTime = "0.0",
                        format = null,
                        playbackSession = session,
                    ),
                )
                val ping = engine.requestHistory.single { it.url.encodedPath == "/api/stats/playback" }
                assertNull(ping.headers["X-Goog-PageId"])
            } finally {
                tube.close()
                client.close()
                Settings.replaceSharedForTest(null)
            }
        }
}
