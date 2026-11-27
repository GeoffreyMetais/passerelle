package fr.passerelle.client.core.platform

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PlatformContractsTest {
    @Test
    fun `stored session holds tokens and publicBaseUrl`() = runTest {
        val session = StoredSession(
            accessToken = "a",
            refreshToken = "r",
            publicBaseUrl = "https://s.domaine.fr",
        )
        assertEquals("a", session.accessToken)
        assertEquals("r", session.refreshToken)
        assertEquals("https://s.domaine.fr", session.publicBaseUrl)
    }
}
