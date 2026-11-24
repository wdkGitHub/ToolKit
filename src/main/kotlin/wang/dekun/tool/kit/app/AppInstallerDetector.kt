package wang.dekun.tool.kit.app

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference


/**
 * 外部应用安装/可用性检测。
 *
 * <p>检测策略（按代价从低到高，全部是纯文件系统操作，不起子进程、不查 Spotlight）：
 * <ol>
 *     <li>在标准应用目录中查找声明的 .app 包；</li>
 *     <li>在 PATH 与常见 bin 目录中查找声明的 CLI 回退命令。</li>
 * </ol>
 * 结果（含“未安装”）都会被缓存，避免每次 update 重复探测。
 *
 * @author WangDeKun
 * <p>
 * Email :  wangdekunemail@gmail.com
 *
 */
object AppInstallerDetector {

    private val logger = Logger.getInstance(AppInstallerDetector::class.java)

    /** 可用性缓存：true/false 都缓存 */
    private val availableCache = ConcurrentHashMap<ExternalApp, Boolean>()

    /** 已安装应用路径缓存，null 表示尚未扫描 */
    private val installedApps = AtomicReference<List<String>?>(null)

    /** 常见 bin 目录：Finder 启动的 IDE 拿不到完整的 PATH，需要补充 */
    private val EXTRA_BIN_DIRS = listOf(
        "/usr/local/bin",
        "/opt/homebrew/bin",
        "/usr/bin",
        "/bin",
    )

    /**
     * 判断外部应用当前是否可用。
     *
     * <p>非 macOS 一律不可用；声明了 `checkInstalled = false` 的应用直接视为可用。
     */
    fun isAvailable(app: ExternalApp): Boolean {
        if (!SystemInfo.isMac) {
            return false
        }
        if (!app.checkInstalled) {
            return true
        }
        return availableCache.computeIfAbsent(app) { detect(it) }
    }

    /**
     * 解析可执行文件：绝对路径直接判断是否存在且可执行，否则按 PATH + 常见 bin 目录查找。
     */
    fun resolveExecutable(command: String): File? {
        if (command.isBlank()) {
            return null
        }
        val file = File(command)
        if (file.isAbsolute) {
            return file.takeIf { it.canExecute() }
        }
        val pathDirs = System.getenv("PATH").orEmpty().split(File.pathSeparatorChar)
        val candidates = (pathDirs + EXTRA_BIN_DIRS)
            .filter { it.isNotBlank() }
            .map { File(it, command) }
        return candidates.firstOrNull { it.canExecute() }
    }

    /**
     * 在标准应用目录中查找 .app 包。
     *
     * @param appName 不含 `.app` 后缀的应用包名，例如 `Fork`、`iTerm`
     */
    fun findAppBundle(appName: String): File? {
        if (appName.isBlank()) {
            return null
        }
        return standardAppDirs()
            .map { File(it, "$appName.app") }
            .firstOrNull { it.isDirectory }
    }

    /**
     * 列出标准应用目录下所有 .app 的绝对路径，供配置页的下拉框使用。
     *
     * <p>结果按应用名排序并缓存：用户在设置里反复打开下拉框不会反复扫目录。
     */
    fun installedAppPaths(): List<String> {
        installedApps.get()?.let { return it }
        val apps = standardAppDirs()
            .asSequence()
            .map { File(it) }
            .filter { it.isDirectory }
            .flatMap { dir ->
                dir.listFiles { file -> file.isDirectory && file.name.endsWith(".app") }
                    ?.asSequence()
                    .orEmpty()
            }
            .map { it.absolutePath }
            .distinct()
            .sortedBy { File(it).name.lowercase() }
            .toList()
        installedApps.set(apps)
        return apps
    }

    /**
     * 把配置里填写的应用（.app 路径或应用名）转成展示用的名字。
     */
    fun appDisplayName(reference: String): String {
        val trimmed = reference.trim()
        if (trimmed.isEmpty()) {
            return ""
        }
        return File(trimmed).name.removeSuffix(".app").ifBlank { trimmed }
    }

    /**
     * 判断配置里填写的应用当前是否可用。
     *
     * <p>填绝对路径 → 判断该路径是否存在；填应用名 → 在标准应用目录里找同名 .app，
     * 或按 PATH 找同名可执行文件。应用名形式允许 `open -a` 自行解析，但名称明显不存在时
     * 就没必要让菜单可点（点了只会失败），因此这里做一次廉价的存在性判断。
     */
    fun isAppUsable(reference: String): Boolean {
        if (!SystemInfo.isMac || reference.isBlank()) {
            return false
        }
        val file = File(reference)
        if (file.isAbsolute) {
            return file.exists()
        }
        return findAppBundle(reference) != null || resolveExecutable(reference) != null
    }

    private fun detect(app: ExternalApp): Boolean {        val appBundle = app.appBundleNames.firstOrNull { findAppBundle(it) != null }
        val executable = app.cliFallbacks.firstOrNull { resolveExecutable(it.first()) != null }?.first()
        val installed = appBundle != null || executable != null
        logger.info("外部应用检测：${app.name} installed=$installed appBundle=$appBundle executable=$executable")
        return installed
    }

    private fun standardAppDirs(): List<String> {
        val userHome = System.getProperty("user.home").orEmpty()
        return listOf(
            "$userHome/Applications",
            "/Applications",
            "/Applications/Utilities",
            "/System/Applications",
            "/System/Applications/Utilities",
        )
    }
}
