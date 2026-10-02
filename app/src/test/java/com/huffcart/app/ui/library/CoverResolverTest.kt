package com.huffcart.app.ui.library

import kotlin.test.Test
import kotlin.test.assertEquals

class CoverResolverTest {

    @Test
    fun importedCoverWinsOverEverything() {
        assertEquals(
            CoverSource.IMPORTED,
            CoverResolver.resolve(importedExists = true, fetchedExists = true, screenshotExists = true),
        )
    }

    @Test
    fun fetchedCoverWinsOverScreenshotAndPlaceholder() {
        assertEquals(
            CoverSource.FETCHED,
            CoverResolver.resolve(importedExists = false, fetchedExists = true, screenshotExists = true),
        )
    }

    @Test
    fun screenshotWinsOverPlaceholder() {
        assertEquals(
            CoverSource.SHOT,
            CoverResolver.resolve(importedExists = false, fetchedExists = false, screenshotExists = true),
        )
    }

    @Test
    fun placeholderWhenNoCoverAvailable() {
        assertEquals(
            CoverSource.PLACEHOLDER,
            CoverResolver.resolve(importedExists = false, fetchedExists = false, screenshotExists = false),
        )
    }

    @Test
    fun coverFileNameUsesNormalizedKey() {
        assertEquals("1942.png", coverFileName("1942 (J).nes"))
        assertEquals("supermariobros.png", coverFileName(" Super Mario Bros. (U) [!].nes"))
        assertEquals("4人麻将.png", coverFileName("4人麻将.nes"))
    }
}
