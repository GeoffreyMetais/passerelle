package fr.passerelle.client.core.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "passerelle_prefs"
private const val KEY_SERVER_URL = "server_url"
private const val KEY_ACCESS_TOKEN = "access_token"
private const val KEY_REFRESH_TOKEN = "refresh_token"
private const val KEY_PUBLIC_BASE_URL = "public_base_url"

class AndroidSessionStore(
    context: Context,
) : SessionStore {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _session = MutableStateFlow(readStored())
    override val session: StateFlow<StoredSession?> = _session.asStateFlow()

    override fun current(): StoredSession? = _session.value

    override fun save(session: StoredSession) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .putString(KEY_REFRESH_TOKEN, session.refreshToken)
            .putString(KEY_PUBLIC_BASE_URL, session.publicBaseUrl)
            .apply()
        _session.value = session
    }

    override fun clear() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_PUBLIC_BASE_URL)
            .apply()
        _session.value = null
    }

    private fun readStored(): StoredSession? {
        val access = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return null
        val publicBaseUrl = prefs.getString(KEY_PUBLIC_BASE_URL, null) ?: ""
        return StoredSession(accessToken = access, refreshToken = refresh, publicBaseUrl = publicBaseUrl)
    }
}

class AndroidServerUrlProvider(
    context: Context,
) : ServerUrlProvider {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override val isServerUrlConfigurable: Boolean = true

    override suspend fun getServerUrl(): String =
        prefs.getString(KEY_SERVER_URL, "")?.trim().orEmpty()

    override suspend fun setServerUrl(url: String) {
        prefs.edit().putString(KEY_SERVER_URL, url.trim().trimEnd('/')).apply()
    }
}

class AndroidPlatformActions(
    private val context: Context,
) : PlatformActions {
    private val appContext = context.applicationContext

    override val deviceName: String = "Android ${Build.MODEL}"

    override fun copyToClipboard(text: String) {
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Passerelle", text))
    }

    override suspend fun shareLink(url: String, title: String): ShareOutcome {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, url)
        }
        val chooser = Intent.createChooser(sendIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(chooser)
        return ShareOutcome.OPENED_NATIVE_SHEET
    }

    override fun openExternalUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    }
}
