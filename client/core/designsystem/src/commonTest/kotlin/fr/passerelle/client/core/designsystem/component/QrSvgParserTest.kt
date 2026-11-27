package fr.passerelle.client.core.designsystem.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class QrSvgParserTest {
    @Test
    fun `parses viewBox and dark module rects ignoring background rect`() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 29 29">
              <rect x="0" y="0" width="29" height="29" fill="#ffffff"/>
              <rect x="4" y="4" width="1" height="1" fill="#000000"/>
              <rect x="5" y="4" width="1" height="1" fill="#000000"/>
            </svg>
        """.trimIndent()

        val parsed = parseQrSvg(svg)
        assertNotNull(parsed)
        assertEquals(29f, parsed.width)
        assertEquals(2, parsed.rects.size)
        assertEquals(4f, parsed.rects.first().x)
    }

    @Test
    fun `parses qrcode crate path output with pixel-scaled modules`() {
        val svg = """<?xml version="1.0" standalone="yes"?><svg xmlns="http://www.w3.org/2000/svg" version="1.1" width="264" height="264" viewBox="0 0 264 264" shape-rendering="crispEdges"><rect x="0" y="0" width="264" height="264" fill="#ffffff"/><path fill="#111111" d="M16 16h8v8H16V16M32 16h8v8H32V16M16 24h24v8H16V24"/></svg>"""

        val parsed = parseQrSvg(svg)
        assertNotNull(parsed)
        assertEquals(264f, parsed.width)
        assertEquals(264f, parsed.height)
        assertEquals(3, parsed.rects.size)
        assertEquals(QrRect(16f, 16f, 8f, 8f), parsed.rects[0])
        assertEquals(QrRect(16f, 24f, 24f, 8f), parsed.rects[2])
    }

    @Test
    fun `ignores path subpaths covering the whole canvas`() {
        val svg = """<svg viewBox="0 0 29 29"><path fill="#ffffff" d="M0 0h29v29H0V0"/><path fill="#000000" d="M4 4h1v1H4V4"/></svg>"""

        val parsed = parseQrSvg(svg)
        assertNotNull(parsed)
        assertEquals(1, parsed.rects.size)
        assertEquals(QrRect(4f, 4f, 1f, 1f), parsed.rects.first())
    }
}
