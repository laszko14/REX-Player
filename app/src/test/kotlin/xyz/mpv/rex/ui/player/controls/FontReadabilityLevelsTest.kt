package xyz.mpv.rex.ui.player.controls

import org.junit.Assert.assertEquals
import org.junit.Test

class FontReadabilityLevelsTest {
    @Test fun levelsHaveDistinctRealFontWeights() {
        listOf(0 to 400, 19 to 400, 20 to 500, 39 to 500,
            40 to 600, 59 to 600, 60 to 700, 74 to 700,
            75 to 800, 89 to 800, 90 to 900, 100 to 900
        ).forEach { (input, expected) ->
            assertEquals(expected, fontWeightForReadability(input).weight)
        }
    }
    @Test fun inputClampsAndOutlinesAreBounded() {
        assertEquals(400, fontWeightForReadability(-1).weight)
        assertEquals(900, fontWeightForReadability(500).weight)
        assertEquals(0f, outlineWidthDpForReadability(-50), .0001f)
        assertEquals(.525f, outlineWidthDpForReadability(50), .0001f)
        assertEquals(1.05f, outlineWidthDpForReadability(100), .0001f)
        assertEquals(1.05f, outlineWidthDpForReadability(500), .0001f)
    }
}
