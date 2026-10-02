package com.huffcart.app.ui.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoverIndexTest {

    /** 模拟 thumbnails.libretro.com NAS 风格目录索引页（节选）。 */
    private val listing = """
        <html><head><title>Index of /Nintendo - Nintendo Entertainment System/Named_Boxarts/</title></head><body>
        <a href="?C=N;O=D">Name</a><br>
        <a href="?C=M;O=A">Last modified</a><br>
        <a href="/Nintendo%20-%20Nintendo%20Entertainment%20System/">Parent Directory</a><br>
        <a href="1942%20(1985-12-11)(Capcom)(JP-US)%5bb%5d.png">1942 (1985-12-11)(Capcom)(JP-US)[b].png</a><br>
        <a href="1942%20(1985-12-11)(Capcom)(JP-US).png">1942 (1985-12-11)(Capcom)(JP-US).png</a><br>
        <a href="Super%20Mario%20Bros.%20(1985-09-13)(Nintendo)(JP).png">Super Mario Bros. (1985-09-13)(Nintendo)(JP).png</a><br>
        <a href="Duck%20Hunt%20%26%20Friends%20(1987)(Nintendo)(US).png">Duck Hunt &amp; Friends (1987)(Nintendo)(US).png</a><br>
        <a href="screenshot.jpg">not-a-cover.jpg</a>
        </body></html>
    """

    @Test
    fun parseMapsBareTitleToExactFileName() {
        val index = CoverIndex.parse(listing)
        assertEquals("1942%20(1985-12-11)(Capcom)(JP-US).png", index["1942"])
    }

    @Test
    fun cleanVariantPreferredOverDumpTags() {
        val index = CoverIndex.parse(listing)
        assertTrue(index.values.none { it.contains("%5b") }, "应优先无 dump 标记的干净版：$index")
    }

    @Test
    fun matchesUserRomNamesAfterNormalize() {
        val index = CoverIndex.parse(listing)
        val marioHref = index[GenreCatalog.normalize(" Super Mario Bros. (U) [!].nes")]
        assertEquals("Super%20Mario%20Bros.%20(1985-09-13)(Nintendo)(JP).png", marioHref)
    }

    @Test
    fun entityEscapesDecodedForMatching() {
        val index = CoverIndex.parse(listing)
        assertEquals("Duck%20Hunt%20%26%20Friends%20(1987)(Nintendo)(US).png", index["duckhuntfriends"])
    }

    @Test
    fun chineseRomNameNotInIndex() {
        val index = CoverIndex.parse(listing)
        assertNull(index[GenreCatalog.normalize("4人麻将.nes")], "中文汉化名未命中 → 静默回退（spec「联网封面静默回退」）")
    }

    @Test
    fun ignoresNonPngAndNavigationLinks() {
        val index = CoverIndex.parse(listing)
        assertTrue(index.values.all { it.endsWith(".png") }, "jpg 与导航链接不进索引：$index")
    }

    @Test
    fun urlAssembly() {
        assertEquals(
            "https://thumbnails.libretro.com/Nintendo%20-%20Nintendo%20Entertainment%20System/Named_Boxarts/",
            CoverIndex.listingUrl(CoverIndex.BOXARTS),
        )
        assertEquals(
            "https://thumbnails.libretro.com/Nintendo%20-%20Nintendo%20Entertainment%20System/Named_Snaps/",
            CoverIndex.listingUrl(CoverIndex.SNAPS),
        )
        assertEquals(
            "https://thumbnails.libretro.com/Nintendo%20-%20Nintendo%20Entertainment%20System/Named_Boxarts/" +
                "1942%20(1985-12-11)(Capcom)(JP-US).png",
            CoverIndex.fileUrl(CoverIndex.BOXARTS, "1942%20(1985-12-11)(Capcom)(JP-US).png"),
        )
    }
}
