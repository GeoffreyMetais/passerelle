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
}
