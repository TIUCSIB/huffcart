package com.huffcart.app.ui.library

/**
 * 内置 FC 游戏词典：分类 + 少量简介（retro-ui-redesign）。
 * 键为规范化文件名（[normalize]）；未命中返回 null，游戏仅出现在「全部」。
 * 词典内容属可调数据，判定与用户认知不符时增补键即可，不构成 spec 行为变更。
 */
object GenreCatalog {

    enum class Genre(val label: String) {
        ACTION("动作"),
        SHOOTER("射击"),
        ADVENTURE("冒险"),
        PUZZLE("益智"),
        FIGHTING("格斗"),
        RACING("赛车"),
    }

    data class Entry(val genre: Genre, val summary: String? = null)

    private val mario = Entry(
        Genre.ACTION,
        "马里奥大叔踏遍八个世界，只为从库巴手中救回桃花公主，跳一跳就是一整个童年。",
    )
    private val contra = Entry(
        Genre.SHOOTER,
        "上上下下左右左右BABA——三十条命的传说，从这片丛林开始。",
    )
    private val battleCity = Entry(
        Genre.SHOOTER,
        "守住老鹰基地，轰碎一辆辆敌军坦克，双人合作才是完全体。",
    )
    private val zelda = Entry(
        Genre.ADVENTURE,
        "海拉尔大陆的地底与迷宫里，藏着三角神力的秘密。",
    )
    private val tetris = Entry(
        Genre.PUZZLE,
        "世界上最著名的方块游戏，一根长条落下的瞬间，快乐很简单。",
    )

    private val catalog: Map<String, Entry> = mapOf(
        "超级马里奥兄弟" to mario,
        "supermariobros" to mario,
        "魂斗罗" to contra,
        "contra" to contra,
        "坦克大战" to battleCity,
        "battlecity" to battleCity,
        "双截龙" to Entry(Genre.FIGHTING),
        "doubledragon" to Entry(Genre.FIGHTING),
        "冒险岛" to Entry(Genre.ADVENTURE),
        "adventureisland" to Entry(Genre.ADVENTURE),
        "俄罗斯方块" to tetris,
        "tetris" to tetris,
        "沙罗曼蛇" to Entry(Genre.SHOOTER),
        "salamander" to Entry(Genre.SHOOTER),
        "洛克人" to Entry(Genre.ACTION),
        "megaman" to Entry(Genre.ACTION),
        "rockman" to Entry(Genre.ACTION),
        "忍者龙剑传" to Entry(Genre.ACTION),
        "ninjagaiden" to Entry(Genre.ACTION),
        "赤色要塞" to Entry(Genre.SHOOTER),
        "jackal" to Entry(Genre.SHOOTER),
        "雪人兄弟" to Entry(Genre.ACTION),
        "snowbros" to Entry(Genre.ACTION),
        "塞尔达传说" to zelda,
        "zelda" to zelda,
        "thelegendofzelda" to zelda,
        "恶魔城" to Entry(Genre.ACTION),
        "castlevania" to Entry(Genre.ACTION),
        "七龙珠" to Entry(Genre.FIGHTING),
        "dragonball" to Entry(Genre.FIGHTING),
        "热血格斗" to Entry(Genre.FIGHTING),
        "马里奥医生" to Entry(Genre.PUZZLE),
        "drmario" to Entry(Genre.PUZZLE),
        "打鸭子" to Entry(Genre.SHOOTER),
        "duckhunt" to Entry(Genre.SHOOTER),
        "超级马里奥" to mario,
        "赤影战士" to Entry(Genre.ACTION),
        "影子传说" to Entry(Genre.ACTION),
        "f1race" to Entry(Genre.RACING, "踩下油门，在连绵的赛道上把对手甩进后视镜。"),
        "f1赛车" to Entry(Genre.RACING, "踩下油门，在连绵的赛道上把对手甩进后视镜。"),
        "radracer" to Entry(Genre.RACING),
        "公路赛车" to Entry(Genre.RACING),
        "roadfighter" to Entry(Genre.RACING),
    )

    /** 规范化：去扩展名 → 去区域/转储标记（(J)/(U)/[!] 等）→ 小写 → 只保留字母数字。 */
    fun normalize(fileName: String): String {
        val noExt = fileName.replace(Regex("\\.(nes|fds|unf|unif)$", RegexOption.IGNORE_CASE), "")
        return noExt
            .replace(Regex("[\\(\\[][^\\)\\]]*[\\)\\]]"), "")
            .lowercase()
            .filter { it.isLetterOrDigit() }
    }

    fun lookup(fileName: String): Entry? = catalog[normalize(fileName)]
}
