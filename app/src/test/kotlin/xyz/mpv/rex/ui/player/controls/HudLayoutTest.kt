package xyz.mpv.rex.ui.player.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HudLayoutTest {
  @Test fun noNewPreferenceRetainsLegacyFlagsAndOrder() {
    assertEquals(
      listOf(HudElement.BATTERY, HudElement.CLOCK, HudElement.REMAINING, HudElement.SPEED),
      resolveHudLayout("", true, true, false, true, true).first,
    )
    assertEquals(emptyList<HudElement>(),
      resolveHudLayout("", true, true, false, true, true).second)
  }

  @Test fun addAndMoveItemsAcrossBothRowsAndRestoreFromStorage() {
    var layout = legacyHudLayout(true, true, true, true, true)
    layout = layout.insert(HudElement.DOWNLOAD, 1)
    layout = layout.insert(HudElement.SPEED, 1, 0)
    layout = layout.move(HudElement.CLOCK, 0, -1)
    val restored = parseHudLayout(layout.encode())!!
    assertEquals(listOf(HudElement.CLOCK, HudElement.BATTERY,
      HudElement.ADJUSTED, HudElement.REMAINING), restored.first)
    assertEquals(listOf(HudElement.SPEED, HudElement.DOWNLOAD), restored.second)
  }

  @Test fun removingAllItemsIsNotConfusedWithUnconfiguredLegacyLayout() {
    val layout = HudLayout(emptyList(), emptyList())
    assertEquals("|", layout.encode())
    assertEquals(layout, parseHudLayout(layout.encode()))
    assertNull(parseHudLayout(""))
    assertFalse(resolveHudLayout("|", true, true, true, true, true).items.isNotEmpty())
  }

  @Test fun malformedOrDuplicateIdsDoNotCrashOrShowExtraItems() {
    val parsed = parseHudLayout("clock,clock,invalid,download|clock,battery,download")!!
    assertEquals(listOf(HudElement.CLOCK, HudElement.DOWNLOAD), parsed.first)
    assertEquals(listOf(HudElement.BATTERY), parsed.second)
    assertNull(parseHudLayout("not-a-layout"))
  }

  @Test fun shiftingAtRowBoundariesAndAddingExistingItemsIsSafe() {
    val original = HudLayout(listOf(HudElement.SPEED, HudElement.CLOCK), emptyList())
    assertEquals(original, original.move(HudElement.SPEED, 0, -1))
    assertEquals(original, original.move(HudElement.CLOCK, 0, 1))
    assertEquals(original, original.move(HudElement.DOWNLOAD, 0, 1))
    val relocated = original.insert(HudElement.SPEED, 1)
    assertTrue(HudElement.SPEED !in relocated.first)
    assertEquals(listOf(HudElement.SPEED), relocated.second)
  }
}
