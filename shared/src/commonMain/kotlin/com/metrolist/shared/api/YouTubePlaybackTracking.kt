package com.metrolist.shared.api

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.extraction.PlaybackTrackingData
import com.metrolist.innertubex.extraction.YtConfigParser
import com.metrolist.innertubex.extraction.generateClientPlaybackNonce
import com.metrolist.innertubex.models.YouTubeClient
import com.metrolist.innertubex.utils.sha1
import com.metrolist.shared.api.YouTubeRepository.PlaybackTrackingSession
import com.metrolist.shared.auth.supportedYouTubeAuthCredential
import com.metrolist.shared.network.bodyAsTextLimited
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.sanitizeDataSyncId
import com.metrolist.shared.youtube.logging.YtLogger
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Clock

internal data class ResolvedPlaybackTracking(
    val data: PlaybackTrackingData,
    val atrUrl: String? = null,
    val atrAfterSeconds: Long = DEFAULT_ATR_SECONDS,
) {
    companion object {
        const val DEFAULT_ATR_SECONDS = 5L
    }
}

internal data class WebRemixSessionScope(
    val clientVersion: String?,
    val delegatedSessionId: String?,
    val rawDataSyncId: String?,
    val personalAccountId: String?,
    val sanitizedDataSyncId: String?,
    val visitorData: String?,
    val authUser: String?,
    val loggedIn: Boolean,
)

internal class YouTubePlaybackTracking(
    private val innerTube: InnerTube,
    private val ytConfigParserProvider: () -> YtConfigParser? = { null },
    private val fetchWebRemixSessionContext: Boolean = false,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private data class ScopeCacheKey(
        val cookie: String,
        val authUser: String,
        val dataSyncId: String?,
    )

    private data class ResolvedAuthContext(
        val cookie: String?,
        val sapisid: String?,
        val authUser: String,
        val pageId: String?,
        val onBehalfOfUser: String?,
        val visitorData: String?,
        val clientVersion: String,
    )

    private data class ClientPlatformInfo(
        val browser: String?,
        val browserVersion: String?,
        val os: String?,
        val osVersion: String?,
    )

    private val scopeMutex = Mutex()
    private var cachedScopeKey: ScopeCacheKey? = null
    private var cachedScope: WebRemixSessionScope? = null

    suspend fun getPlaybackTrackingUrl(
        videoId: String,
        playlistId: String? = null,
    ): String? =
        fetchResolvedPlaybackTracking(
            videoId = videoId,
            playlistId = playlistId,
            clientPlaybackNonce = generateClientPlaybackNonce(),
            playbackSession = null,
        )?.data?.playbackUrl

    fun playbackTrackingSession(): PlaybackTrackingSession = PlaybackTrackingSession(innerTube.sessionSnapshot())

    fun isPlaybackTrackingSessionCurrent(playbackSession: PlaybackTrackingSession): Boolean =
        innerTube.sessionSnapshot() == playbackSession.snapshot

    internal fun playbackReportingAccountKey(playbackSession: PlaybackTrackingSession = playbackTrackingSession()): String? {
        val session = playbackSession.snapshot
        supportedYouTubeAuthCredential(session.cookie.orEmpty()) ?: return null
        val identity = session.dataSyncId?.takeIf { it.isNotBlank() } ?: return null
        return "datasync:$identity:${session.authUser}"
    }

    internal suspend fun getPlaybackTracking(
        videoId: String,
        clientPlaybackNonce: String,
        playbackSession: PlaybackTrackingSession,
    ): PlaybackTrackingData? = getResolvedPlaybackTracking(videoId, clientPlaybackNonce, playbackSession)?.data

    internal suspend fun getResolvedPlaybackTracking(
        videoId: String,
        clientPlaybackNonce: String,
        playbackSession: PlaybackTrackingSession,
    ): ResolvedPlaybackTracking? =
        if (!isPlaybackReportingEnabled() || !isPlaybackTrackingSessionCurrent(playbackSession)) {
            null
        } else {
            fetchResolvedPlaybackTracking(videoId, null, clientPlaybackNonce, playbackSession)
        }

    private suspend fun fetchResolvedPlaybackTracking(
        videoId: String,
        playlistId: String?,
        clientPlaybackNonce: String,
        playbackSession: PlaybackTrackingSession?,
    ): ResolvedPlaybackTracking? =
        try {
            val session = playbackSession?.snapshot ?: innerTube.sessionSnapshot()
            var scope = ensureWebRemixSessionScope(session)
            val parser = ytConfigParserProvider()
            val useLoginCookies = !session.cookie.isNullOrBlank()
            val signatureTimestamp = fetchSignatureTimestamp(parser, videoId, useLoginCookies)
            var httpResponse =
                requestPlayerTracking(
                    videoId = videoId,
                    playlistId = playlistId,
                    session = session,
                    scope = scope,
                    signatureTimestamp = signatureTimestamp,
                )
            if (isAuthRejected(httpResponse.status) && fetchWebRemixSessionContext) {
                scope = ensureWebRemixSessionScope(session, forceRefresh = true)
                httpResponse =
                    requestPlayerTracking(
                        videoId = videoId,
                        playlistId = playlistId,
                        session = session,
                        scope = scope,
                        signatureTimestamp = signatureTimestamp,
                    )
            }
            YtLogger.w("HistorySync", "Tracking status=${httpResponse.status.value}")
            var json = httpResponse.bodyLimited<JsonObject>(PLAYER_NEXT_RESPONSE_LIMIT)
            var parsed = parseTrackingResponse(json, clientPlaybackNonce)

            // Retry once with embed player config if watch config failed or lacked tracking.
            if (parsed == null && parser != null) {
                val embedSts = fetchEmbeddedSignatureTimestamp(parser, videoId)
                if (embedSts != null && embedSts != signatureTimestamp) {
                    val retryResponse =
                        requestPlayerTracking(
                            videoId = videoId,
                            playlistId = playlistId,
                            session = session,
                            scope = scope,
                            signatureTimestamp = embedSts,
                        )
                    YtLogger.w("HistorySync", "Tracking embed sts retry status=${retryResponse.status.value}")
                    json = retryResponse.bodyLimited(PLAYER_NEXT_RESPONSE_LIMIT)
                    parsed = parseTrackingResponse(json, clientPlaybackNonce)
                }
            }

            if (parsed == null) {
                val playability = json["playabilityStatus"]?.jsonObject
                val status = playability?.get("status")?.jsonPrimitive?.contentOrNull
                val reason = playability?.get("reason")?.jsonPrimitive?.contentOrNull
                YtLogger.w(
                    "HistorySync",
                    "Missing playbackTracking for $videoId status=$status reason=$reason sts=$signatureTimestamp",
                )
            }
            parsed
        } catch (e: CancellationException) {
            throw e
        } catch (error: Exception) {
            YtLogger.e("HistorySync", "Tracking failed type=${error::class.simpleName}")
            null
        }

    private suspend fun fetchSignatureTimestamp(
        parser: YtConfigParser?,
        videoId: String,
        useLoginCookies: Boolean,
    ): Int? {
        if (parser == null) return null
        return try {
            parser.fetchConfig(videoId, useLoginCookies = useLoginCookies).signatureTimestamp
                ?: parser.fetchEmbeddedConfig(videoId, useLoginCookies = false).signatureTimestamp
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            fetchEmbeddedSignatureTimestamp(parser, videoId)
        }
    }

    private suspend fun fetchEmbeddedSignatureTimestamp(
        parser: YtConfigParser,
        videoId: String,
    ): Int? =
        try {
            parser.fetchEmbeddedConfig(videoId, useLoginCookies = false).signatureTimestamp
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    private suspend fun requestPlayerTracking(
        videoId: String,
        playlistId: String?,
        session: InnerTube.SessionSnapshot,
        scope: WebRemixSessionScope?,
        signatureTimestamp: Int?,
    ): HttpResponse {
        val auth = resolveAuthContext(session, scope)
        val payload =
            buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", YouTubeClient.WEB_REMIX.clientName)
                        put("clientVersion", auth.clientVersion)
                        session.locale.hl
                            .takeIf { it.isNotBlank() }
                            ?.let { put("hl", it) }
                        session.locale.gl
                            .takeIf { it.isNotBlank() }
                            ?.let { put("gl", it) }
                        auth.visitorData?.let { put("visitorData", it) }
                    }
                    auth.onBehalfOfUser?.let { onBehalf ->
                        putJsonObject("user") {
                            put("lockedSafetyMode", false)
                            put("onBehalfOfUser", onBehalf)
                        }
                    }
                }
                put("videoId", videoId)
                playlistId?.let { put("playlistId", it) }
                put("contentCheckOk", true)
                put("racyCheckOk", true)
                putJsonObject("playbackContext") {
                    putJsonObject("contentPlaybackContext") {
                        put("html5Preference", "HTML5_PREF_WANTS")
                        put("referer", "$MUSIC_ORIGIN/watch?v=$videoId")
                        signatureTimestamp?.let { put("signatureTimestamp", it) }
                    }
                }
            }.toString()

        return innerTube.httpClient.post(PLAYER_ENDPOINT) {
            contentType(ContentType.Application.Json)
            applyMusicHeaders(session, auth, includeClientHeaders = true)
            setBody(payload)
        }
    }

    private fun parseTrackingResponse(
        response: JsonObject,
        clientPlaybackNonce: String,
    ): ResolvedPlaybackTracking? {
        val tracking = response["playbackTracking"]?.jsonObject ?: return null
        val playbackUrl = tracking.trackingBaseUrl("videostatsPlaybackUrl") ?: return null
        val watchtimeUrl = tracking.trackingBaseUrl("videostatsWatchtimeUrl")
        val atrObject = tracking["atrUrl"]?.jsonObject
        val atrUrl =
            atrObject
                ?.get("baseUrl")
                ?.jsonPrimitive
                ?.contentOrNull
                ?.takeIf { it.isNotBlank() }
        val atrAfterSeconds =
            atrObject
                ?.get("elapsedMediaTimeSeconds")
                ?.jsonPrimitive
                ?.longOrNull
                ?.coerceAtLeast(0L)
                ?: ResolvedPlaybackTracking.DEFAULT_ATR_SECONDS
        val scheduledFlushWalltimeSeconds =
            (tracking["videostatsScheduledFlushWalltimeSeconds"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.intOrNull ?: it.jsonPrimitive.contentOrNull?.toIntOrNull() }
                .orEmpty()
        val defaultFlushIntervalSeconds =
            tracking["videostatsDefaultFlushIntervalSeconds"]
                ?.jsonPrimitive
                ?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }

        val data =
            PlaybackTrackingData(
                clientPlaybackNonce = clientPlaybackNonce,
                playbackUrl = playbackUrl,
                watchtimeUrl = watchtimeUrl,
                scheduledFlushWalltimeSeconds = scheduledFlushWalltimeSeconds,
                defaultFlushIntervalSeconds = defaultFlushIntervalSeconds,
                resolvedAtEpochMs = nowMs(),
            )
        return ResolvedPlaybackTracking(
            data = data,
            atrUrl = atrUrl,
            atrAfterSeconds = atrAfterSeconds,
        )
    }

    internal fun isPlaybackReportingEnabled(): Boolean =
        innerTube.hasSapCookieAuth() && Settings.shared.ytmSync && !Settings.shared.pauseListenHistory

    internal suspend fun registerPlayback(
        playbackTrackingUrl: String,
        clientPlaybackNonce: String,
        currentMediaTime: String,
        format: Int?,
        playbackSession: PlaybackTrackingSession,
    ): Boolean =
        sendStatsPing(
            rawUrl = playbackTrackingUrl,
            expectedPath = PLAYBACK_STATS_PATH,
            logName = "Playback",
            playbackSession = playbackSession,
        ) { _, auth ->
            applyCommonStatsParams(clientPlaybackNonce, auth, playbackSession.snapshot)
            parameterIfMissing("cmt", currentMediaTime)
            url.parameters["state"] = "playing"
            format?.let { url.parameters["fmt"] = it.toString() }
        }

    internal suspend fun registerAtr(
        atrTrackingUrl: String,
        clientPlaybackNonce: String,
        playbackSession: PlaybackTrackingSession,
    ): Boolean =
        sendStatsPing(
            rawUrl = atrTrackingUrl,
            expectedPath = ATR_STATS_PATH,
            logName = "ATR",
            playbackSession = playbackSession,
        ) { _, auth ->
            // Real atrUrl already includes ver/c/cver/atr_params; fill only if absent and bind to this play's cpn.
            parameterIfMissing("ver", STATS_VERSION)
            parameterIfMissing("c", YouTubeClient.WEB_REMIX.clientName)
            parameterIfMissing("cver", auth.clientVersion)
            url.parameters["cpn"] = clientPlaybackNonce
            applyLocaleStatsParams(playbackSession.snapshot)
        }

    internal suspend fun registerWatchtime(
        watchtimeTrackingUrl: String,
        clientPlaybackNonce: String,
        startTimes: String,
        endTimes: String,
        currentMediaTime: String,
        realTime: String,
        final: Boolean,
        format: Int?,
        playbackSession: PlaybackTrackingSession,
    ): Boolean =
        sendStatsPing(
            rawUrl = watchtimeTrackingUrl,
            expectedPath = WATCHTIME_STATS_PATH,
            logName = "Watchtime",
            playbackSession = playbackSession,
        ) { _, auth ->
            applyCommonStatsParams(clientPlaybackNonce, auth, playbackSession.snapshot)
            url.parameters["st"] = startTimes
            url.parameters["et"] = endTimes
            url.parameters["cmt"] = currentMediaTime
            url.parameters["rt"] = realTime
            url.parameters["state"] = if (final) "paused" else "playing"
            if (final) {
                url.parameters["final"] = "1"
            }
            format?.let { url.parameters["fmt"] = it.toString() }
        }

    private suspend fun sendStatsPing(
        rawUrl: String,
        expectedPath: String,
        logName: String,
        playbackSession: PlaybackTrackingSession,
        configureParams: HttpRequestBuilder.(HttpRequestBuilder, ResolvedAuthContext) -> Unit,
    ): Boolean {
        if (!isPlaybackReportingEnabled() || !isPlaybackTrackingSessionCurrent(playbackSession)) return false
        val normalizedUrl = normalizePlaybackStatsUrl(rawUrl, expectedPath) ?: return false
        val session = playbackSession.snapshot
        var scope = ensureWebRemixSessionScope(session)
        var response = executeStatsGet(normalizedUrl, session, scope, configureParams)
        if (isAuthRejected(response.status) && fetchWebRemixSessionContext && isPlaybackTrackingSessionCurrent(playbackSession)) {
            scope = ensureWebRemixSessionScope(session, forceRefresh = true)
            response = executeStatsGet(normalizedUrl, session, scope, configureParams)
        }
        if (!response.status.isSuccess()) {
            YtLogger.w("HistorySync", "$logName report status=${response.status.value}")
        }
        return response.status.isSuccess()
    }

    private suspend fun executeStatsGet(
        normalizedUrl: String,
        session: InnerTube.SessionSnapshot,
        scope: WebRemixSessionScope?,
        configureParams: HttpRequestBuilder.(HttpRequestBuilder, ResolvedAuthContext) -> Unit,
    ): HttpResponse {
        val auth = resolveAuthContext(session, scope)
        return innerTube.httpClient.get(normalizedUrl) {
            applyMusicHeaders(session, auth, includeClientHeaders = false)
            configureParams(this, auth)
        }
    }

    private suspend fun ensureWebRemixSessionScope(
        session: InnerTube.SessionSnapshot,
        forceRefresh: Boolean = false,
    ): WebRemixSessionScope? {
        if (!fetchWebRemixSessionContext) return null
        val cookie = session.cookie?.takeIf { it.isNotBlank() } ?: return null
        val key = ScopeCacheKey(cookie = cookie, authUser = session.authUser, dataSyncId = session.dataSyncId)
        scopeMutex.withLock {
            if (!forceRefresh && cachedScopeKey == key && cachedScope != null) {
                return cachedScope
            }
            val parsed =
                try {
                    val sapisid = extractSapisid(cookie, session.sapisid)
                    val response =
                        innerTube.httpClient.get(MUSIC_SHELL_URL) {
                            header(HttpHeaders.UserAgent, YouTubeClient.WEB_REMIX.userAgent)
                            acceptLanguageHeader(session)?.let { header(HttpHeaders.AcceptLanguage, it) }
                            header(HttpHeaders.Cookie, cookie)
                            sapisid?.let { header(HttpHeaders.Authorization, sapisidHash(it, nowSeconds())) }
                        }
                    if (!response.status.isSuccess()) {
                        null
                    } else {
                        response.bodyAsTextLimited(SMALL_RESPONSE_LIMIT)?.let(::parseWebRemixSessionScope)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            if (parsed != null) {
                cachedScopeKey = key
                cachedScope = parsed
            } else if (cachedScopeKey != key) {
                cachedScopeKey = null
                cachedScope = null
            }
            return cachedScope
        }
    }

    private fun resolveAuthContext(
        session: InnerTube.SessionSnapshot,
        scope: WebRemixSessionScope?,
    ): ResolvedAuthContext {
        val cookie = session.cookie?.takeIf { it.isNotBlank() }
        val sapisid = extractSapisid(cookie, session.sapisid)
        val rawSessionSync = session.dataSyncId?.takeIf { it.isNotBlank() }
        val rawHasDelimiter = rawSessionSync?.contains("||") == true
        val rawChannelPart = rawSessionSync?.takeIf { rawHasDelimiter }?.substringBefore("||")?.takeIf { it.isNotBlank() }
        val sanitizedSessionSync = rawSessionSync?.let(::sanitizeDataSyncId)?.takeIf { it.isNotBlank() }

        val pageId =
            when {
                rawChannelPart != null -> rawChannelPart

                scope?.loggedIn == true && sanitizedSessionSync == null -> scope.delegatedSessionId

                scope?.loggedIn == true && sanitizedSessionSync == scope.delegatedSessionId -> scope.delegatedSessionId

                scope?.loggedIn == true &&
                    scope.personalAccountId != null &&
                    sanitizedSessionSync != null &&
                    sanitizedSessionSync != scope.personalAccountId -> sanitizedSessionSync

                else -> null
            }
        val onBehalfOfUser =
            sanitizedSessionSync
                ?: scope?.takeIf { it.loggedIn }?.let { it.delegatedSessionId ?: it.sanitizedDataSyncId }
        val visitorData =
            scope
                ?.takeIf { it.loggedIn }
                ?.visitorData
                ?.takeIf { it.isNotBlank() }
                ?: session.visitorData?.takeIf { it.isNotBlank() }
        val authUser =
            session.authUser.takeIf { it.isNotBlank() }
                ?: scope?.authUser?.takeIf { it.isNotBlank() }
                ?: "0"
        val clientVersion =
            scope?.clientVersion?.takeIf { it.isNotBlank() }
                ?: YouTubeClient.WEB_REMIX.clientVersion

        return ResolvedAuthContext(
            cookie = cookie,
            sapisid = sapisid,
            authUser = authUser,
            pageId = pageId,
            onBehalfOfUser = onBehalfOfUser,
            visitorData = visitorData,
            clientVersion = clientVersion,
        )
    }

    private fun HttpRequestBuilder.applyMusicHeaders(
        session: InnerTube.SessionSnapshot,
        auth: ResolvedAuthContext,
        includeClientHeaders: Boolean,
    ) {
        header(HttpHeaders.Origin, MUSIC_ORIGIN)
        header(HEADER_X_ORIGIN, MUSIC_ORIGIN)
        header(HttpHeaders.Referrer, "$MUSIC_ORIGIN/")
        header(HttpHeaders.UserAgent, YouTubeClient.WEB_REMIX.userAgent)
        acceptLanguageHeader(session)?.let { header(HttpHeaders.AcceptLanguage, it) }
        auth.visitorData?.let { header(HEADER_VISITOR_ID, it) }
        if (includeClientHeaders) {
            header(HEADER_CLIENT_NAME, YouTubeClient.WEB_REMIX.clientId)
            header(HEADER_CLIENT_VERSION, auth.clientVersion)
        }
        auth.cookie?.let { cookie ->
            header(HttpHeaders.Cookie, cookie)
            header(HEADER_AUTH_USER, auth.authUser)
            auth.pageId?.let { header(HEADER_PAGE_ID, it) }
            auth.sapisid?.let { header(HttpHeaders.Authorization, sapisidHash(it, nowSeconds())) }
        }
    }

    private fun HttpRequestBuilder.applyCommonStatsParams(
        clientPlaybackNonce: String,
        auth: ResolvedAuthContext,
        session: InnerTube.SessionSnapshot,
    ) {
        val platform = clientPlatformInfo
        parameterIfMissing("ver", STATS_VERSION)
        parameterIfMissing("c", YouTubeClient.WEB_REMIX.clientName)
        url.parameters["cver"] = auth.clientVersion
        url.parameters["cpn"] = clientPlaybackNonce
        parameterIfMissing("cplayer", "UNIPLAYER")
        platform.browser?.let { parameterIfMissing("cbr", it) }
        platform.browserVersion?.let { parameterIfMissing("cbrver", it) }
        platform.os?.let { parameterIfMissing("cos", it) }
        platform.osVersion?.let { parameterIfMissing("cosver", it) }
        applyLocaleStatsParams(session)
    }

    private fun HttpRequestBuilder.applyLocaleStatsParams(session: InnerTube.SessionSnapshot) {
        session.locale.hl
            .takeIf { it.isNotBlank() }
            ?.let { parameterIfMissing("hl", it.replace('-', '_')) }
        session.locale.gl
            .takeIf { it.isNotBlank() }
            ?.let { parameterIfMissing("cr", it) }
    }

    private fun HttpRequestBuilder.parameterIfMissing(
        key: String,
        value: String,
    ) {
        if (url.parameters[key].isNullOrBlank()) {
            parameter(key, value)
        }
    }

    private fun normalizePlaybackStatsUrl(
        value: String,
        expectedPath: String,
    ): String? {
        val parsed = runCatching { Url(value) }.getOrNull() ?: return null
        if (parsed.protocol != URLProtocol.HTTPS || parsed.port != 443 || parsed.user != null || parsed.password != null) return null
        if (parsed.encodedPath != expectedPath || parsed.encodedPath !in ALLOWED_STATS_PATHS) return null
        return when (parsed.host) {
            in ALLOWED_STATS_HOSTS -> value
            else -> null
        }
    }

    private fun nowSeconds(): Long = nowMs() / 1_000L

    private fun isAuthRejected(status: HttpStatusCode): Boolean =
        status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden

    private fun JsonObject.trackingBaseUrl(key: String): String? =
        this[key]
            ?.jsonObject
            ?.get("baseUrl")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }

    private fun acceptLanguageHeader(session: InnerTube.SessionSnapshot): String? {
        val hl = session.locale.hl.takeIf { it.isNotBlank() } ?: return null
        val gl = session.locale.gl.takeIf { it.isNotBlank() }
        return if (gl != null) "$hl-$gl,$hl;q=0.9" else hl
    }

    private fun sapisidHash(
        sapisid: String,
        timestampSeconds: Long,
    ): String {
        val digest = sha1("$timestampSeconds $sapisid $MUSIC_ORIGIN")
        return "SAPISIDHASH ${timestampSeconds}_$digest"
    }

    private fun extractSapisid(
        cookie: String?,
        fallbackSapisid: String?,
    ): String? {
        if (!cookie.isNullOrBlank()) {
            val jar = parseCookieJar(cookie)
            SAPISID_COOKIE_NAMES.firstNotNullOfOrNull { jar[it] }?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return fallbackSapisid?.takeIf { it.isNotBlank() }
    }

    private fun parseCookieJar(cookieHeader: String): Map<String, String> =
        buildMap {
            for (part in cookieHeader.split(';')) {
                val eqIndex = part.indexOf('=')
                if (eqIndex <= 0) continue
                val name = part.substring(0, eqIndex).trim()
                val value = part.substring(eqIndex + 1).trim()
                if (name.isNotEmpty() && value.isNotEmpty()) {
                    put(name, value)
                }
            }
        }

    companion object {
        private const val MUSIC_ORIGIN = "https://music.youtube.com"
        private const val MUSIC_SHELL_URL = "$MUSIC_ORIGIN/"
        private const val PLAYER_ENDPOINT = "$MUSIC_ORIGIN/youtubei/v1/player?prettyPrint=false"

        private const val PLAYBACK_STATS_PATH = "/api/stats/playback"
        private const val ATR_STATS_PATH = "/api/stats/atr"
        private const val WATCHTIME_STATS_PATH = "/api/stats/watchtime"
        private const val STATS_VERSION = "2"

        private const val HEADER_X_ORIGIN = "X-Origin"
        private const val HEADER_VISITOR_ID = "X-Goog-Visitor-Id"
        private const val HEADER_AUTH_USER = "X-Goog-AuthUser"
        private const val HEADER_PAGE_ID = "X-Goog-PageId"
        private const val HEADER_CLIENT_NAME = "X-YouTube-Client-Name"
        private const val HEADER_CLIENT_VERSION = "X-YouTube-Client-Version"

        private val SAPISID_COOKIE_NAMES = listOf("SAPISID", "__Secure-3PAPISID", "__Secure-1PAPISID")
        private val ALLOWED_STATS_PATHS = setOf(PLAYBACK_STATS_PATH, ATR_STATS_PATH, WATCHTIME_STATS_PATH)
        private val ALLOWED_STATS_HOSTS = setOf("music.youtube.com", "s.youtube.com", "www.youtube.com")

        private val CONFIG_LOGGED_IN = Regex(""""LOGGED_IN"\s*:\s*(true|false)""")
        private val CONFIG_DATASYNC_ID = Regex(""""DATASYNC_ID"\s*:\s*"([^"]+)"""")
        private val CONFIG_DELEGATED_SESSION_ID = Regex(""""DELEGATED_SESSION_ID"\s*:\s*"([^"]+)"""")
        private val CONFIG_SESSION_INDEX = Regex(""""SESSION_INDEX"\s*:\s*"?(\d+)""")
        private val CONFIG_VISITOR_DATA = Regex(""""VISITOR_DATA"\s*:\s*"([^"]+)"""")
        private val CONFIG_CLIENT_VERSION = Regex(""""INNERTUBE_CLIENT_VERSION"\s*:\s*"([^"]+)"""")

        private val FIREFOX_UA_REGEX = Regex("""Firefox/([0-9.]+)""")
        private val CHROME_UA_REGEX = Regex("""Chrome/([0-9.]+)""")
        private val WINDOWS_OS_REGEX = Regex("""Windows NT ([0-9.]+)""")
        private val MAC_OS_REGEX = Regex("""Mac OS X ([0-9_]+)""")

        private val clientPlatformInfo: ClientPlatformInfo by lazy {
            parseClientPlatform(YouTubeClient.WEB_REMIX.userAgent)
        }

        private fun parseClientPlatform(userAgent: String): ClientPlatformInfo {
            val firefox = FIREFOX_UA_REGEX.find(userAgent)?.groupValues?.get(1)
            val chrome = CHROME_UA_REGEX.find(userAgent)?.groupValues?.get(1)
            val (browser, browserVersion) =
                when {
                    firefox != null -> "Firefox" to firefox
                    chrome != null -> "Chrome" to chrome
                    else -> null to null
                }
            val windows = WINDOWS_OS_REGEX.find(userAgent)?.groupValues?.get(1)
            val mac =
                MAC_OS_REGEX
                    .find(userAgent)
                    ?.groupValues
                    ?.get(1)
                    ?.replace('_', '.')
            val (os, osVersion) =
                when {
                    windows != null -> "Windows" to windows
                    mac != null -> "Macintosh" to mac
                    else -> null to null
                }
            return ClientPlatformInfo(
                browser = browser,
                browserVersion = browserVersion,
                os = os,
                osVersion = osVersion,
            )
        }

        internal fun parseWebRemixSessionScope(html: String): WebRemixSessionScope {
            val loggedIn = CONFIG_LOGGED_IN.find(html)?.groupValues?.get(1) == "true"
            val clientVersion =
                CONFIG_CLIENT_VERSION
                    .find(html)
                    ?.groupValues
                    ?.get(1)
                    ?.takeIf { it.isNotBlank() }
            if (!loggedIn) {
                return WebRemixSessionScope(
                    clientVersion = clientVersion,
                    delegatedSessionId = null,
                    rawDataSyncId = null,
                    personalAccountId = null,
                    sanitizedDataSyncId = null,
                    visitorData = null,
                    authUser = null,
                    loggedIn = false,
                )
            }
            val delegatedSessionId =
                CONFIG_DELEGATED_SESSION_ID
                    .find(html)
                    ?.groupValues
                    ?.get(1)
                    ?.takeIf { it.isNotBlank() }
            val rawDataSyncId =
                CONFIG_DATASYNC_ID
                    .find(html)
                    ?.groupValues
                    ?.get(1)
                    ?.takeIf { it.isNotBlank() }
            val personalAccountId =
                when {
                    rawDataSyncId == null -> null
                    rawDataSyncId.contains("||") -> rawDataSyncId.substringAfter("||").takeIf { it.isNotBlank() }
                    delegatedSessionId == null -> rawDataSyncId
                    else -> null
                }
            val sanitizedDataSyncId = rawDataSyncId?.let(::sanitizeDataSyncId)?.takeIf { it.isNotBlank() }
            val visitorData =
                CONFIG_VISITOR_DATA
                    .find(html)
                    ?.groupValues
                    ?.get(1)
                    ?.takeIf { it.isNotBlank() }
            val authUser =
                CONFIG_SESSION_INDEX
                    .find(html)
                    ?.groupValues
                    ?.get(1)
                    ?.takeIf { it.isNotBlank() }
            return WebRemixSessionScope(
                clientVersion = clientVersion,
                delegatedSessionId = delegatedSessionId,
                rawDataSyncId = rawDataSyncId,
                personalAccountId = personalAccountId,
                sanitizedDataSyncId = sanitizedDataSyncId,
                visitorData = visitorData,
                authUser = authUser,
                loggedIn = true,
            )
        }
    }
}
