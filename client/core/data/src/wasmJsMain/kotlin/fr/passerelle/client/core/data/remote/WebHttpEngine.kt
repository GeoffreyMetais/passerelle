package fr.passerelle.client.core.data.remote

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js

internal actual fun createDefaultHttpClientEngine(): HttpClientEngine = Js.create()
