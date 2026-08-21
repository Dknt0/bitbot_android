package com.bitbot

import com.bitbot.data.model.ButtonConfig
import com.bitbot.data.model.ButtonLayoutCodec
import com.bitbot.data.model.ButtonLayouts
import com.bitbot.util.Constants.ButtonEvents
import com.bitbot.util.Constants.PolicyMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonConfigTest {

    // --- Event allow-list filter ---

    @Test
    fun `fixed events are button events`() {
        listOf(
            "stop", "power_on", "enable_record", "start",
            "init_pose", "run_policy"
        ).forEach { assertTrue("expected $it to be a button event", ButtonEvents.isButtonEvent(it)) }
    }

    @Test
    fun `policy events match the regex`() {
        listOf(
            "enable_standing_policy", "enable_warking_policy", "enable_robust_policy",
            "enable_something_new_policy"
        ).forEach { assertTrue("expected $it to be a button event", ButtonEvents.isButtonEvent(it)) }
    }

    @Test
    fun `internal and analog events are not button events`() {
        listOf(
            "policy_switch",              // kernel-internal transition event
            "set_vel_x", "set_vel_y", "set_vel_w",   // analog velocity channels
            "velo_x_increase", "velo_x_decrease",     // keyboard-style velocity nudges
            "velo_y_increase", "velo_w_decrease",
            "nav_trigger",                // not in backend control list
            "enable_policy",              // empty middle segment
            "", "start_extra"
        ).forEach { assertFalse("expected $it to be rejected", ButtonEvents.isButtonEvent(it)) }
    }

    // --- Policy <-> event mapping ---

    @Test
    fun `policy events map to policy modes`() {
        assertEquals(PolicyMode.STANDING, ButtonEvents.policyModeForEvent("enable_standing_policy"))
        assertEquals(PolicyMode.WALKING, ButtonEvents.policyModeForEvent("enable_warking_policy"))
        assertEquals(PolicyMode.ROBUST, ButtonEvents.policyModeForEvent("enable_robust_policy"))
    }

    @Test
    fun `unknown policy events do not map to a local mode`() {
        assertNull(ButtonEvents.policyModeForEvent("enable_something_policy"))
        assertNull(ButtonEvents.policyModeForEvent("start"))
    }

    @Test
    fun `policy mode round trip through event name`() {
        PolicyMode.entries.forEach { mode ->
            assertEquals(mode, ButtonEvents.policyModeForEvent(ButtonEvents.eventForPolicyMode(mode)))
        }
    }

    // --- Default layout ---

    @Test
    fun `default layout only includes offered events`() {
        val layout = ButtonLayouts.defaultLayout(listOf("start", "stop", "enable_standing_policy", "set_vel_x"))
        val names = layout.map { it.eventName }
        assertEquals(listOf("start", "enable_standing_policy"), names)
    }

    @Test
    fun `default layout with full event set mirrors original panel`() {
        val layout = ButtonLayouts.defaultLayout(ButtonEvents.FALLBACK_EVENTS)
        assertEquals(
            listOf(
                "power_on", "init_pose", "start", "run_policy",
                "enable_standing_policy", "enable_warking_policy", "enable_robust_policy"
            ),
            layout.map { it.eventName }
        )
        // All positions normalized and within the screen
        layout.forEach {
            assertTrue(it.x in 0f..1f)
            assertTrue(it.y in 0f..1f)
        }
    }

    // --- Codec ---

    @Test
    fun `layout codec round trip preserves fields`() {
        val layout = listOf(
            ButtonConfig(eventName = "start", label = "Start", x = 0.42f, y = 0.47f, sizeDp = 96f, colorARGB = 0xFF2196F3L)
        )
        val decoded = ButtonLayoutCodec.decode(ButtonLayoutCodec.encode(layout))
        assertEquals(layout, decoded)
    }

    @Test
    fun `decode of empty layout yields empty list`() {
        assertEquals(emptyList<ButtonConfig>(), ButtonLayoutCodec.decode("[]"))
    }

    @Test
    fun `decode of garbage yields null`() {
        assertNull(ButtonLayoutCodec.decode("not json"))
        assertNull(ButtonLayoutCodec.decode("{\"unexpected\":1}"))
    }

    // --- Labels ---

    @Test
    fun `known events get short labels, unknown get prettified`() {
        assertEquals("PowerOn", ButtonLayouts.prettifyLabel("power_on"))
        assertEquals("Stand", ButtonLayouts.prettifyLabel("enable_standing_policy"))
        assertEquals("Enable Jump Policy", ButtonLayouts.prettifyLabel("enable_jump_policy"))
    }
}
