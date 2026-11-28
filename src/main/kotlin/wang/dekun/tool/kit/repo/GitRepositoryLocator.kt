package wang.dekun.tool.kit.repo

import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.util.concurrent.ConcurrentHashMap


/**
 * git 仓库信息定位器：解析目标路径所属的仓库根目录与远程地址。
 *
 * <p>**只用文件系统**：自底向上查找 `.git`，再解析 `.git/config`（兼容 worktree / submodule），
 * 不启动 git 子进程，也不经过 git4idea 的 [com.intellij.dvcs.repo.VcsRepositoryManager]。
 *
 * <p>为什么不用 git4idea：`Action.update()` 是在平台的**不可取消读锁**里执行的，
 * 而 `GitRepositoryManager.getRepositoryForFile()` 在仓库尚未初始化时会同步创建仓库并启动
 * `git` 子进程（`GitTextHandler.createProcess` / `git worktree list`），平台会判定
 * “slow operation may freeze UI”，把 Plugin to blame 指向本插件，同时还会把 git4idea 的异步
 * 初始化拉成同步，触发 “State of repository ... is not synchronized” 告警。
 * 文件系统方案既没有这个副作用，也不依赖 Git4Idea 的加载状态。
 *
 * <p>由于 `update()` 会被右键菜单与工具栏频繁调用，解析结果按路径缓存：
 * 命中一律返回缓存，正/负结果使用不同 TTL，避免新增仓库后长期看不到菜单项。
 *
 * <p>这里只产出**原始** remote 地址，浏览器地址由 [GitRemoteUrlResolver] 负责转换
 * （未配置规则走原有 https 逻辑，命中域名/IP 规则时替换协议与 Web 端口）。
 * URL 转换不进缓存，因此设置页改完规则可以立即生效。
 *
 * @author WangDeKun
 * <p>
 * Email :  wangdekunemail@gmail.com
 *
 */
object GitRepositoryLocator {

    private val logger = Logger.getInstance(GitRepositoryLocator::class.java)

    private const val ORIGIN_REMOTE = "origin"
    private const val GIT_DIR_NAME = ".git"
    private const val GIT_CONFIG_FILE_NAME = "config"
    private const val GIT_COMMON_DIR_FILE_NAME = "commondir"
    private const val GIT_DIR_PREFIX = "gitdir:"
    private const val MAX_SEARCH_DEPTH = 64

    /** 命中仓库的缓存时长：仓库根与远程地址很少变，长一点可以显著减少 update 的磁盘开销 */
    private const val POSITIVE_TTL_MS = 10_000L

    /** 未命中仓库的缓存时长：短一点，用户新建仓库后菜单能很快出现 */
    private const val NEGATIVE_TTL_MS = 2_000L

    /** 缓存条目上限，超过后整体清空（条目极小，不值得做 LRU） */
    private const val MAX_CACHE_SIZE = 512

    private class CacheEntry(val info: Info, val expireAt: Long)

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    /**
     * 一次解析结果。
     *
     * @param root      仓库根目录的绝对路径，未找到仓库时为 null
     * @param remoteUrl 仓库配置的首个远程地址（原始形式，可能是 ssh），没有时为 null
     */
    data class Info(val root: String?, val remoteUrl: String?) {

        companion object {
            val EMPTY = Info(null, null)
        }
    }

    /**
     * 解析指定路径（文件或目录）所属的仓库信息。
     *
     * <p>只做文件系统操作（stat + 读取极小的 `.git/config`），可以在 `update()` 里安全调用；
     * 结果按路径缓存，重复调用几乎零开销。
     *
     * @param path 文件或目录的绝对路径
     */
    fun locate(path: String?): Info {
        if (path.isNullOrBlank()) {
            return Info.EMPTY
        }
        val now = System.currentTimeMillis()
        cache[path]?.let { entry ->
            if (entry.expireAt > now) {
                return entry.info
            }
        }
        val info = locateByFileSystem(path)
        putCache(path, info, now)
        return info
    }

    private fun putCache(path: String, info: Info, now: Long) {
        if (cache.size >= MAX_CACHE_SIZE) {
            cache.clear()
        }
        val ttl = if (info.root != null) POSITIVE_TTL_MS else NEGATIVE_TTL_MS
        cache[path] = CacheEntry(info, now + ttl)
    }

    /** 清空缓存：仓库被创建/删除等场景可主动调用 */
    fun invalidate() {
        cache.clear()
    }

    /** 纯文件系统定位：找仓库根 + 读 `.git/config` 里的**原始** remote 地址 */
    private fun locateByFileSystem(path: String): Info {
        val root = findRepositoryRoot(path) ?: return Info.EMPTY
        return Info(root, readRemoteUrl(root))
    }

    /** 自底向上查找包含 `.git` 的目录 */
    private fun findRepositoryRoot(path: String): String? {
        var current: File? = File(path).let { if (it.isDirectory) it else it.parentFile }
        var depth = 0
        while (current != null && depth <= MAX_SEARCH_DEPTH) {
            if (File(current, GIT_DIR_NAME).exists()) {
                return current.absolutePath
            }
            current = current.parentFile
            depth++
        }
        return null
    }

    /** 解析 `.git/config` 中的远程地址，兼容 git worktree（`.git` 是文件）的情况 */
    private fun readRemoteUrl(rootPath: String): String? {
        val configFile = resolveConfigFile(File(rootPath, GIT_DIR_NAME)) ?: return null
        if (!configFile.isFile) {
            return null
        }
        val urls = mutableListOf<Pair<String, String>>()
        var currentRemote: String? = null
        try {
            configFile.forEachLine { rawLine ->
                val line = rawLine.trim()
                when {
                    line.isEmpty() || line.startsWith("#") || line.startsWith(";") -> Unit
                    line.startsWith("[") -> currentRemote = parseRemoteSection(line)
                    currentRemote != null -> {
                        val key = line.substringBefore('=', "").trim()
                        if ("url".equals(key, ignoreCase = true)) {
                            val url = line.substringAfter('=', "").trim()
                            if (url.isNotEmpty()) {
                                urls.add(currentRemote.orEmpty() to url)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("解析 git 配置失败：${configFile.absolutePath}", e)
            return null
        }
        val origin = urls.firstOrNull { it.first == ORIGIN_REMOTE }
        return (origin ?: urls.firstOrNull())?.second
    }

    /** `[remote "origin"]` -> `origin`，非 remote 段落返回 null */
    private fun parseRemoteSection(line: String): String? {
        val section = line.removePrefix("[").substringBefore("]").trim()
        if (!section.startsWith("remote", ignoreCase = true)) {
            return null
        }
        val quotedName = section.substringAfter('"', "").substringBefore('"')
        if (quotedName.isNotBlank()) {
            return quotedName
        }
        return section.substringAfter(' ', "").trim().ifBlank { null }
    }

    /**
     * 定位 config 文件：普通仓库是 `<root>/.git/config`；
     * worktree 场景下 `.git` 是文本文件，需要先读 `gitdir:` 再读 `commondir`。
     */
    private fun resolveConfigFile(gitEntry: File): File? {
        if (gitEntry.isDirectory) {
            return File(gitEntry, GIT_CONFIG_FILE_NAME)
        }
        if (!gitEntry.isFile) {
            return null
        }
        val content = runCatching { gitEntry.readText() }.getOrNull().orEmpty()
        val gitDirPath = content.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith(GIT_DIR_PREFIX, ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val baseDir = gitEntry.parentFile ?: File(System.getProperty("user.home").orEmpty())
        val gitDir = File(gitDirPath).let { if (it.isAbsolute) it else File(baseDir, gitDirPath) }
        return File(commonDirOf(gitDir), GIT_CONFIG_FILE_NAME)
    }

    /** worktree 的 gitDir 下会有一个 `commondir` 文件指向真正的仓库元数据目录 */
    private fun commonDirOf(gitDir: File): File {
        val commonDirFile = File(gitDir, GIT_COMMON_DIR_FILE_NAME)
        if (!commonDirFile.isFile) {
            return gitDir
        }
        val relative = runCatching { commonDirFile.readText().trim() }.getOrNull().orEmpty()
        return if (relative.isEmpty()) gitDir else File(gitDir, relative)
    }
}
