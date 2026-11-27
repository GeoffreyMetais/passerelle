package fr.passerelle.client.core.platform

import kotlinx.coroutines.flow.StateFlow

data class StoredSession(
    val accessToken: String,
    val refreshToken: String,
    val publicBaseUrl: String,
)

interface SessionStore {
    val session: StateFlow<StoredSession?>
    fun current(): StoredSession?
    fun save(session: StoredSession)
    fun clear()
}

interface ServerUrlProvider {
    val isServerUrlConfigurable: Boolean
    suspend fun getServerUrl(): String
    suspend fun setServerUrl(url: String)
}

enum class ShareOutcome {
    OPENED_NATIVE_SHEET,
    COPIED_TO_CLIPBOARD,
}

interface PlatformActions {
    val deviceName: String
    fun copyToClipboard(text: String)
    suspend fun shareLink(url: String, title: String): ShareOutcome
    fun openExternalUrl(url: String)
}
