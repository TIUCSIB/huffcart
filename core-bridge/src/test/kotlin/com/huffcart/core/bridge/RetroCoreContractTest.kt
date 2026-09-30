package com.huffcart.core.bridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 用最小假实现验证 RetroCore 契约可以在无 Android 依赖的纯 JVM 环境实现与消费。 */
private class FakeCore : RetroCore {
    private val pressed = mutableSetOf<RetroButton>()
    private var loaded = false

    override val name = "fake"

    override fun loadRom(romPath: String): LoadResult =
        if (romPath.endsWith(".nes")) {
            loaded = true
            LoadResult.OK
        } else {
            LoadResult.INVALID_ROM
        }

    override fun runFrame(): Frame {
        check(loaded) { "runFrame before loadRom" }
        val pixel = if (RetroButton.A in pressed) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        return Frame(
            intArrayOf(pixel), VideoInfo(1, 1),
            shortArrayOf(0, 0), AudioInfo(48_000), audioSamples = 2,
        )
    }

    override fun setButton(player: Int, button: RetroButton, pressed: Boolean) {
        if (pressed) this.pressed += button else this.pressed -= button
    }

    override fun reset() = pressed.clear()

    override fun unload() {
        loaded = false
    }
}

class RetroCoreContractTest {

    @Test
    fun loadRomAcceptsNesPath() {
        assertEquals(LoadResult.OK, FakeCore().loadRom("/data/game.nes"))
    }

    @Test
    fun loadRomRejectsNonNesPath() {
        assertEquals(LoadResult.INVALID_ROM, FakeCore().loadRom("/data/game.bin"))
    }

    @Test
    fun runFrameBeforeLoadRomFails() {
        val exception = runCatching { FakeCore().runFrame() }.exceptionOrNull()
        assertTrue(exception is IllegalStateException)
    }

    @Test
    fun runFrameReflectsButtonState() {
        val core = FakeCore()
        core.loadRom("/data/game.nes")
        assertEquals(0xFF000000.toInt(), core.runFrame().video[0])
        core.setButton(player = 0, button = RetroButton.A, pressed = true)
        assertEquals(0xFFFFFFFF.toInt(), core.runFrame().video[0])
        core.setButton(player = 0, button = RetroButton.A, pressed = false)
        assertEquals(0xFF000000.toInt(), core.runFrame().video[0])
    }

    @Test
    fun resetClearsPressedButtons() {
        val core = FakeCore()
        core.loadRom("/data/game.nes")
        core.setButton(0, RetroButton.START, true)
        core.reset()
        assertEquals(0xFF000000.toInt(), core.runFrame().video[0])
    }

    @Test
    fun unloadReturnsCoreToUnloadedState() {
        val core = FakeCore()
        core.loadRom("/data/game.nes")
        core.unload()
        val exception = runCatching { core.runFrame() }.exceptionOrNull()
        assertTrue(exception is IllegalStateException)
    }

    @Test
    fun frameCarriesAudioSampleCount() {
        val core = FakeCore()
        core.loadRom("/data/game.nes")
        assertEquals(2, core.runFrame().audioSamples)
    }
}
