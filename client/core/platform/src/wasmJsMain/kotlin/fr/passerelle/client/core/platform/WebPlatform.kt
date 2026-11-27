package fr.passerelle.client.core.platform

import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.w3c.fetch.Response
import kotlin.js.Promise

private const val KEY_SERVER_URL = "passerelle_server_url"
private const val KEY_ACCESS_TOKEN = "passerelle_access_token"
private const val KEY_REFRESH_TOKEN = "passerelle_refresh_token"
private const val KEY_PUBLIC_BASE_URL = "passerelle_public_base_url"
private const val DEFAULT_LOCAL_API_URL = "http://localhost:8080"

@Serializable
private data class WebConfigJson(
    val apiBaseUrl: String? = null,
)

class WebSessionStore : SessionStore {
    private val _session = MutableStateFlow(readStored())
    override val session: StateFlow<StoredSession?> = _session.asStateFlow()

    override fun current(): StoredSession? = _session.value

    override fun save(session: StoredSession) {
        window.localStorage.setItem(KEY_ACCESS_TOKEN, session.accessToken)
        window.localStorage.setItem(KEY_REFRESH_TOKEN, session.refreshToken)
        window.localStorage.setItem(KEY_PUBLIC_BASE_URL, session.publicBaseUrl)
        _session.value = session
    }

    override fun clear() {
        window.localStorage.removeItem(KEY_ACCESS_TOKEN)
        window.localStorage.removeItem(KEY_REFRESH_TOKEN)
        window.localStorage.removeItem(KEY_PUBLIC_BASE_URL)
        _session.value = null
    }

    private fun readStored(): StoredSession? {
        val access = window.localStorage.getItem(KEY_ACCESS_TOKEN) ?: return null
        val refresh = window.localStorage.getItem(KEY_REFRESH_TOKEN) ?: return null
        val publicBaseUrl = window.localStorage.getItem(KEY_PUBLIC_BASE_URL) ?: ""
        return StoredSession(accessToken = access, refreshToken = refresh, publicBaseUrl = publicBaseUrl)
    }
}

class WebServerUrlProvider : ServerUrlProvider {
    private val json = Json { ignoreUnknownKeys = true }
    private var cachedUrl: String? = null
    private var configLoadedFromFile: Boolean = false

    override val isServerUrlConfigurable: Boolean
        get() = !configLoadedFromFile

    override suspend fun getServerUrl(): String {
        cachedUrl?.let { return it }

        val fromConfig = fetchConfigJson()
        if (fromConfig != null) {
            configLoadedFromFile = true
            cachedUrl = fromConfig
            return fromConfig
        }

        val stored = window.localStorage.getItem(KEY_SERVER_URL)?.trim()?.trimEnd('/')
        if (!stored.isNullOrEmpty()) {
            cachedUrl = stored
            return stored
        }

        cachedUrl = DEFAULT_LOCAL_API_URL
        return DEFAULT_LOCAL_API_URL
    }

    override suspend fun setServerUrl(url: String) {
        val normalized = url.trim().trimEnd('/')
        if (normalized.isNotEmpty()) {
            cachedUrl = normalized
            window.localStorage.setItem(KEY_SERVER_URL, normalized)
        }
    }

    private suspend fun fetchConfigJson(): String? {
        return try {
            val response = window.fetch("./config.json").await<Response>()
            if (!response.ok) return null
            val body = response.text().await<JsString>().toString()
            val parsed = json.decodeFromString<WebConfigJson>(body)
            parsed.apiBaseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
        } catch (_: Throwable) {
            null
        }
    }
}

@JsFun("(title, url) => { if (navigator.share) { return navigator.share({ title: title, url: url }).then(() => true).catch(() => false); } return Promise.resolve(false); }")
private external fun jsNavigatorShare(title: String, url: String): Promise<JsBoolean>

@JsFun("(text) => { if (navigator.clipboard && navigator.clipboard.writeText) { return navigator.clipboard.writeText(text).then(() => true).catch(() => false); } return Promise.resolve(false); }")
private external fun jsCopyClipboard(text: String): Promise<JsBoolean>

class WebPlatformActions : PlatformActions {
    override val deviceName: String = "PWA Web"

    override fun copyToClipboard(text: String) {
        jsCopyClipboard(text)
    }

    override suspend fun shareLink(url: String, title: String): ShareOutcome {
        val sharedNatively = try {
            jsNavigatorShare(title, url).await<JsBoolean>().toBoolean()
        } catch (_: Throwable) {
            false
        }
        return if (sharedNatively) {
            ShareOutcome.OPENED_NATIVE_SHEET
        } else {
            copyToClipboard(url)
            ShareOutcome.COPIED_TO_CLIPBOARD
        }
    }

    override fun openExternalUrl(url: String) {
        window.open(url, "_blank")
    }
}
