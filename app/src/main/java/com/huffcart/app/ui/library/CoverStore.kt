package com.huffcart.app.ui.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * 封面存取（game-cover-art 决策 1/2）：covers/ 目录读写 + libretro 抓取。
 * 导入与联网封面共用 covers/<规范化名>.png 槽位——导入覆盖写即「导入 > 联网」；
 * 联网任何一步失败一律静默返回 false，调用方回退占位图，不阻塞不报错（spec「联网封面静默回退」）。
 */
object CoverStore {

    private const val CONNECT_TIMEOUT_MS = 5000
    // libretro 目录索引页 8000+ 条目达数 MB 且服务器出包缓慢（实测 ~20KB/s，需分钟级），
    // 读超时须远大于连接超时；图片文件小得多，15s 足够
    private const val INDEX_READ_TIMEOUT_MS = 20000
    private const val IMAGE_READ_TIMEOUT_MS = 15000
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android) huffcart/0.1 cover-fetch"

    /** 索引落盘缓存 TTL：列表随库更新极慢，7 天足够新鲜。 */
    private const val INDEX_TTL_MS = 7L * 24 * 60 * 60 * 1000

    /** 预抓取并发上限：图源限速，过高只会互相挤占带宽。 */
    private const val PREFETCH_CONCURRENCY = 6

    /** 会话级抓取作用域：调用方退出组合不打断落盘中的抓取，落盘后其他调用方复查即得。 */
    private val fetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 内存去重：同游戏并发抓取只发一次，其余等待同一 Job（任务 1.1）。 */
    private val inFlight = HashMap<String, Job>()

    /** 索引进程内缓存：成功才缓存，失败下次重试。 */
    private val indexCache = HashMap<String, Map<String, String>>()
    private val indexMutex = Mutex()

    fun coversDir(context: Context): File = File(context.filesDir, "covers")

    fun coverFile(context: Context, gameName: String): File =
        coversDir(context).resolve(coverFileName(gameName))

    /** 运行截图槽位（与 covers/ 分离：导入/联网覆盖语义不受截图干扰）。 */
    fun screenshotsDir(context: Context): File = File(context.filesDir, "covershots")

    fun screenshotFile(context: Context, gameName: String): File =
        screenshotsDir(context).resolve(coverFileName(gameName))

    /** 显示层是否有任一可用封面文件（联网抓取/导入/截图）。 */
    fun hasAnyDisplayCover(context: Context, gameName: String): Boolean =
        coverFile(context, gameName).exists() || screenshotFile(context, gameName).exists()

    /** 显示解码：导入/联网封面优先，其次运行截图；长边限 ~512px，损坏文件返回 null。 */
    fun decodeDisplayCover(context: Context, gameName: String): Bitmap? =
        decodeCover(coverFile(context, gameName)) ?: decodeCover(screenshotFile(context, gameName))

    /**
     * 游戏线程截帧落盘（game-cover-art 任务 2.3）：异步压缩写 covershots/。
     * [bitmap] 由调用方复制独有，本方法消费后回收；已有任一封面则直接丢弃不写。
     */
    fun saveScreenshotAsync(context: Context, gameName: String, bitmap: Bitmap) {
        val appContext = context.applicationContext
        fetchScope.launch {
            val file = screenshotFile(appContext, gameName)
            if (file.exists()) {
                bitmap.recycle()
                return@launch
            }
            runCatching {
                file.parentFile?.mkdirs()
                file.outputStream().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                }
            }
            bitmap.recycle()
        }
    }

    /** 确保该游戏有封面落盘；返回落盘后封面文件是否存在。已存在（导入或既往抓取）直接 true。 */
    suspend fun ensureCover(context: Context, gameName: String): Boolean {
        val file = coverFile(context, gameName)
        if (file.exists()) return true
        val key = GenreCatalog.normalize(gameName)
        if (key.isEmpty()) return false
        val job = synchronized(inFlight) {
            inFlight[key] ?: run {
                val created = fetchScope.launch { fetchCover(context, key) }
                inFlight[key] = created
                created.invokeOnCompletion { synchronized(inFlight) { inFlight.remove(key, created) } }
                created
            }
        }
        job.join()
        return file.exists()
    }

    /** SAF 导入封面：解码校验 → 长边限 ~512px 重采样 → 覆盖写 covers/<规范化名>.png。 */
    fun importCover(context: Context, gameName: String, uri: Uri): Boolean {
        return try {
            val target = coverFile(context, gameName)
            if (target.name == ".png") return false
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
            val opts = BitmapFactory.Options().apply {
                inSampleSize = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 512)
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return false
            target.parentFile?.mkdirs()
            val saved = target.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            bitmap.recycle()
            saved
        } catch (t: Throwable) {
            false
        }
    }

    /** 显示解码：长边限 ~512px 采样，损坏文件返回 null（回退占位图）。 */
    fun decodeCover(file: File): Bitmap? {
        if (!file.exists()) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 512)
            }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        }.getOrNull()
    }

    /** 抓取主路径：候选键（本名 + 中文别名）依次查 Boxarts 索引 → 抓图；未命中再试 Snaps。 */
    private suspend fun fetchCover(context: Context, key: String): Boolean = withContext(Dispatchers.IO) {
        val target = coversDir(context).resolve("$key.png")
        val candidates = buildList(2) {
            add(key)
            CoverAliases.target(key)?.let { add(it) }
        }
        val boxIndex = indexFor(context, CoverIndex.BOXARTS)
        val boxHit = candidates.firstNotNullOfOrNull { boxIndex?.get(it) }
        if (boxHit != null && fetchImage(CoverIndex.fileUrl(CoverIndex.BOXARTS, boxHit), target)) {
            return@withContext true
        }
        val snapsIndex = indexFor(context, CoverIndex.SNAPS)
        val snapsHit = candidates.firstNotNullOfOrNull { snapsIndex?.get(it) } ?: return@withContext false
        fetchImage(CoverIndex.fileUrl(CoverIndex.SNAPS, snapsHit), target)
    }

    /**
     * 库刷新后的后台批量预抓取：限流并发逐个补齐封面，落盘持久。
     * 图片体积 100-500KB 且图源限速，全部完成需数十分钟；期间界面即时回退占位图不受影响。
     */
    fun prefetchAll(context: Context, gameNames: List<String>) {
        fetchScope.launch {
            val semaphore = Semaphore(PREFETCH_CONCURRENCY)
            coroutineScope {
                gameNames.forEach { name ->
                    launch {
                        semaphore.withPermit {
                            runCatching { ensureCover(context, name) }
                        }
                    }
                }
            }
        }
    }

    /** 目录索引三级来源：内存缓存 → 落盘缓存（TTL 内）→ 网络拉取并写落盘缓存。 */
    private suspend fun indexFor(context: Context, collection: String): Map<String, String>? =
        indexMutex.withLock {
            indexCache[collection]?.let { return@withLock it }
            cachedIndexFile(context, collection).takeIf { it.exists() }?.let { file ->
                if (System.currentTimeMillis() - file.lastModified() < INDEX_TTL_MS) {
                    parseCachedIndex(file)?.let { return@withLock it.also { m -> indexCache[collection] = m } }
                }
            }
            val fresh = fetchText(CoverIndex.listingUrl(collection))
                ?.let { CoverIndex.parse(it) }
                ?: return@withLock null
            indexCache[collection] = fresh
            runCatching {
                val file = cachedIndexFile(context, collection)
                file.parentFile?.mkdirs()
                file.writeText(fresh.entries.joinToString("\n") { "${it.key}\t${it.value}" })
            }
            fresh
        }

    /** 落盘缓存文件：cacheDir 下按集合命名，行格式 "<key>\t<encodedHref>"。 */
    private fun cachedIndexFile(context: Context, collection: String): File =
        File(context.cacheDir, "covers_index_$collection.txt")

    private fun parseCachedIndex(file: File): Map<String, String>? = runCatching {
        file.readLines().mapNotNull { line ->
            val sep = line.indexOf('\t')
            if (sep <= 0) null else line.take(sep) to line.substring(sep + 1)
        }.toMap().ifEmpty { null }
    }.getOrNull()

    private fun connect(url: String, readTimeoutMs: Int): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = readTimeoutMs
            setRequestProperty("User-Agent", USER_AGENT)
        }

    private fun fetchText(url: String): String? = runCatching {
        val conn = connect(url, INDEX_READ_TIMEOUT_MS)
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
            conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** 抓图落盘：校验 PNG/JPEG 魔数（防错误页污染槽位），写临时文件后原子改名。 */
    private fun fetchImage(url: String, target: File): Boolean = runCatching {
        if (target.exists()) return@runCatching false
        val conn = connect(url, IMAGE_READ_TIMEOUT_MS)
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@runCatching false
            conn.inputStream.use { input ->
                val head = ByteArray(4)
                if (readFully(input, head) < head.size) return@runCatching false
                val isPng = head[0] == 0x89.toByte() && head[1] == 'P'.code.toByte() &&
                    head[2] == 'N'.code.toByte() && head[3] == 'G'.code.toByte()
                val isJpeg = head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte()
                if (!isPng && !isJpeg) return@runCatching false
                target.parentFile?.mkdirs()
                val tmp = File(target.parentFile, target.name + ".part")
                tmp.outputStream().use { output ->
                    output.write(head)
                    input.copyTo(output)
                }
                if (tmp.length() > head.size) tmp.renameTo(target) else tmp.delete()
            }
        } finally {
            conn.disconnect()
        }
    }.getOrNull() == true

    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var read = 0
        while (read < buffer.size) {
            val n = input.read(buffer, read, buffer.size - read)
            if (n < 0) break
            read += n
        }
        return read
    }
}
