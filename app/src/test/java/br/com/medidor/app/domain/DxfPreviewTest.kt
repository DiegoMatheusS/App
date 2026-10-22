package br.com.medidor.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DxfPreviewTest {
    @Test fun parsesLinesFromEntitiesOnly() {
        val src = """
0
SECTION
2
ENTITIES
0
LINE
8
PAREDES
10
0
20
0
11
5
21
0
0
LINE
10
5
20
0
11
5
21
4
0
ENDSEC
0
EOF
""".trimIndent()
        val found = DxfPreview.parse(src)
        assertEquals(2,found.size)
        assertEquals(5.0,found[0].x2,0.00001)
        assertEquals(4.0,found[1].y2,0.00001)
    }
    @Test fun refusesUnknownOrEmptyDrawings() {
        assertThrows(IllegalArgumentException::class.java) {
            DxfPreview.parse("0\nSECTION\n2\nENTITIES\n0\nENDSEC\n0\nEOF")
        }
        assertThrows(IllegalArgumentException::class.java) { DxfPreview.parse("hello") }
    }
}
