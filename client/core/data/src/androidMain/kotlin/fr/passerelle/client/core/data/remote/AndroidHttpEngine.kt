package fr.passerelle.client.core.data.remote

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

internal actual fun createDefaultHttpClientEngine(): HttpClientEngine = OkHttp.create()
