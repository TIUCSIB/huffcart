package com.huffcart.app.ui.game

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 存档槽文件布局（audio-settings-and-save-management 任务 2.1）：路径构造、空槽判定、删除、扫描。 */
class SaveSlotStoreTest {

    private fun tempDir(): File = Files.createTempDirectory("slots").toFile()

    @Test
    fun pathsFollowStateSuffixLayout() {
        val dir = tempDir()
        assertEquals(File(dir, "超级玛丽.state0"), SaveSlotStore.stateFile(dir, "超级玛丽.nes", 0))
        assertEquals(File(dir, "超级玛丽.state3.png"), SaveSlotStore.thumbnailFile(dir, "超级玛丽.nes", 3))
    }

    @Test
    fun slotsReportEmptyAndOccupied() {
        val dir = tempDir()
        val rom = "Contra.nes"
        assertTrue(SaveSlotStore.slots(dir, rom).all { it == null })

        SaveSlotStore.writeState(dir, rom, 2, byteArrayOf(1, 2, 3))
        val slots = SaveSlotStore.slots(dir, rom)
        assertEquals(SaveSlotStore.SLOT_COUNT, slots.size)
        assertNull(slots[0])
        assertEquals(2, slots[2]?.slot)
        assertTrue(slots[2]!!.savedAt > 0)
    }

    @Test
    fun deleteRemovesStateAndThumbnailOnlyForThatSlot() {
        val dir = tempDir()
        val rom = "Zelda.nes"
        SaveSlotStore.writeState(dir, rom, 0, byteArrayOf(1))
        SaveSlotStore.writeState(dir, rom, 1, byteArrayOf(2))
        SaveSlotStore.thumbnailFile(dir, rom, 0).writeBytes(byteArrayOf(9))

        SaveSlotStore.deleteSlot(dir, rom, 0)

        assertFalse(SaveSlotStore.stateFile(dir, rom, 0).exists())
        assertFalse(SaveSlotStore.thumbnailFile(dir, rom, 0).exists())
        assertTrue(SaveSlotStore.stateFile(dir, rom, 1).exists())
    }

    @Test
    fun gamesWithSlotsDedupesAndIgnoresSrmAndState4() {
        val dir = tempDir()
        File(dir, "Bubble.state0").writeBytes(byteArrayOf(1))
        File(dir, "Bubble.state2.png").writeBytes(byteArrayOf(1))
        File(dir, "Bubble.srm").writeBytes(byteArrayOf(1))
        File(dir, "Mario.state3").writeBytes(byteArrayOf(1))
        // state4 不是合法槽位（4 槽上限），不得计入
        File(dir, "Rogue.state4").writeBytes(byteArrayOf(1))

        assertEquals(listOf("Bubble", "Mario"), SaveSlotStore.gamesWithSlots(dir))
    }

    @Test
    fun legacySingleSlotFileIsSlotOne() {
        val dir = tempDir()
        // 历史单槽布局：<romName>.state0 无侧车图
        File(dir, "Legacy.state0").writeBytes(byteArrayOf(1))

        val slots = SaveSlotStore.slots(dir, "Legacy.nes")
        assertEquals(0, slots[0]?.slot)
        assertEquals(listOf("Legacy"), SaveSlotStore.gamesWithSlots(dir))
    }

    @Test
    fun baseNamePlatformSchemeIsolatesCrossPlatformSameName() {
        // FC 历史约定剥 .nes;GB/GBC 保留完整文件名——跨平台同名互不串档
        assertEquals("塞尔达", SaveSlotStore.baseName("塞尔达.nes"))
        assertEquals("宝可梦.gb", SaveSlotStore.baseName("宝可梦.gb"))
        assertEquals("水晶.gbc", SaveSlotStore.baseName("水晶.gbc"))

        val dir = tempDir()
        SaveSlotStore.writeState(dir, "塞尔达.nes", 0, byteArrayOf(1))
        SaveSlotStore.writeState(dir, "塞尔达.gb", 0, byteArrayOf(2))
        assertEquals("塞尔达.state0", SaveSlotStore.stateFile(dir, "塞尔达.nes", 0).name)
        assertEquals("塞尔达.gb.state0", SaveSlotStore.stateFile(dir, "塞尔达.gb", 0).name)
        assertEquals(2, SaveSlotStore.gamesWithSlots(dir).size)
    }
}
