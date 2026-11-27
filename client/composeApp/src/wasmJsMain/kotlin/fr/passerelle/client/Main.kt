package fr.passerelle.client

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import fr.passerelle.client.di.initKoin
import fr.passerelle.client.ui.PasserelleAppRoot
import kotlinx.browser.document

@JsFun("() => { if (window.__hidePasserelleSplash) window.__hidePasserelleSplash(); }")
private external fun hideSplashScreen()

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initKoin()
    val body = document.body ?: return
    ComposeViewport(body) {
        LaunchedEffect(Unit) {
            hideSplashScreen()
        }
        PasserelleAppRoot()
    }
}
