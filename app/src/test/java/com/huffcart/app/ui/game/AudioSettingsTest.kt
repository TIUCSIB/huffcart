package com.huffcart.app.ui.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 声音设置值对象（audio-settings-and-save-management 任务 1.1）：默认值与增益回环。 */
class AudioSettingsTest {

    @Test
    fun defaultsAreMaxVolumeUnmuted() {
        val settings = AudioSettings()
        assertEquals(100, settings.volumePercent)
        assertFalse(settings.muted)
        assertFalse(settings.ffMuted)
    }

    @Test
    fun gainFollowsVolumePercent() {
        assertEquals(1f, AudioSettings().gain)
        assertEquals(0.5f, AudioSettings(volumePercent = 50).gain)
        assertEquals(0f, AudioSettings(volumePercent = 0).gain)
    }

    @Test
    fun muteForcesZeroGainRegardlessOfVolume() {
        assertEquals(0f, AudioSettings(volumePercent = 80, muted = true).gain)
    }

    @Test
    fun outOfRangeVolumeIsClamped() {
        assertEquals(1f, AudioSettings(volumePercent = 250).gain)
        assertEquals(0f, AudioSettings(volumePercent = -3).gain)
    }

    @Test
    fun fastForwardMuteOnlyWhileFastForwarding() {
        val settings = AudioSettings(ffMuted = true)
        assertEquals(0f, settings.gainDuringFastForward(ffActive = true))
        assertEquals(settings.gain, settings.gainDuringFastForward(ffActive = false))
    }

    @Test
    fun fastForwardKeepsSoundByDefault() {
        val settings = AudioSettings(ffMuted = false)
        assertEquals(settings.gain, settings.gainDuringFastForward(ffActive = true))
        assertTrue(settings.gain > 0f)
    }

    /** audio-latency-tuning 任务 1.1：默认档位为平衡；三档缓冲毫秒值随档位递增。 */
    @Test
    fun latencyDefaultsToBalanced() {
        assertEquals(AudioLatency.BALANCED, AudioSettings().latencyTier)
        assertTrue(AudioLatency.LOW.bufferMs < AudioLatency.BALANCED.bufferMs)
        assertTrue(AudioLatency.BALANCED.bufferMs < AudioLatency.STABLE.bufferMs)
    }

    /** audio-latency-tuning 任务 1.1：存储值解析——合法值透传，空/非法回落平衡（决策 6）。 */
    @Test
    fun latencyParsingFallsBackToBalanced() {
        assertEquals(AudioLatency.LOW, AudioLatency.from("LOW"))
        assertEquals(AudioLatency.STABLE, AudioLatency.from("STABLE"))
        assertEquals(AudioLatency.BALANCED, AudioLatency.from(null))
        assertEquals(AudioLatency.BALANCED, AudioLatency.from("junk"))
        assertEquals(AudioLatency.BALANCED, AudioLatency.from("balanced"))
    }
}
