package xyz.mpv.rex.ui.player.controls

import org.junit.Assert.assertEquals
import org.junit.Test

class LuaMinimalSoftStyleTest {
    @Test fun defaultTransparencyPreservesRequestedFadeLevels() {
        assertEquals(.55f, luaSoftEffectiveAlpha(LUA_SOFT_IDLE_ALPHA, 20), .0001f)
        assertEquals(.85f, luaSoftEffectiveAlpha(LUA_SOFT_TOUCH_ALPHA, 20), .0001f)
        assertEquals(.90f, luaSoftEffectiveAlpha(LUA_SOFT_ACTIVE_ALPHA, 20), .0001f)
    }

    @Test fun sliderCanFadeToZeroWithoutChangingHitTargets() {
        assertEquals(0f, luaSoftEffectiveAlpha(LUA_SOFT_IDLE_ALPHA, 100), .0001f)
        assertEquals(.6875f, luaSoftEffectiveAlpha(LUA_SOFT_IDLE_ALPHA, 0), .0001f)
    }

    @Test fun transparencyIsClampedForCorruptedSavedSettings() {
        assertEquals(0f, luaSoftEffectiveAlpha(1f, 200), .0001f)
        assertEquals(1f, luaSoftEffectiveAlpha(1f, -20), .0001f)
    }
}
