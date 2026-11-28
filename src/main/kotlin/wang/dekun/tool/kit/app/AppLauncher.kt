package wang.dekun.tool.kit.app

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import wang.dekun.tool.kit.context.ActionTarget
import wang.dekun.tool.kit.context.ActionTargetResolver
import wang.dekun.tool.kit.utils.Utils
import java.io.File
import java.util.concurrent.TimeUnit


/**
 * 外部应用启动器。
 *
 * <p>启动方式优先级（“更好的打开第三方软件的方式”）：
 * <ol>
 *     <li>`open -b &lt;bundleId&gt; &lt;path&gt;`：由 LaunchServices 解析安装位置，
 *         不需要硬编码可执行文件路径，也不需要 Spotlight 查找；</li>
 *     <li>应用自带的 CLI 回退（例如 `fork open &lt;path&gt;`、`stree &lt;path&gt;`）；</li>
 *     <li>浏览器 URL 直接走平台 [BrowserUtil.browse]，不做子进程、不依赖 git-open 脚本。</li>
 * </ol>
 *
 * <p>所有启动都在后台线程池执行，**绝不阻塞 EDT**；失败统一走通知。
 *
 * @author WangDeKun
 * <p>
 * Email :  wangdekunemail@gmail.com
 *
 */
object AppLauncher {

    private val logger = Logger.getInstance(AppLauncher::class.java)

    private const val OPEN_COMMAND = "/usr/bin/open"

    private const val PROCESS_TIMEOUT_SECONDS = 15L

    private const val NOTIFICATION_TITLE = "ToolKit"

    /**
     * 异步启动外部应用。
     *
     * @param project    当前项目，欢迎界面时为 null
     * @param app        目标外部应用
     * @param rawTargets 未补齐 git 信息的原始目标（由 EDT 上读取 DataKey 得到）
     */
    fun launchAsync(project: Project?, app: ExternalApp, rawTargets: List<ActionTarget>) {
        if (rawTargets.isEmpty()) {
            return
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                launch(project, app, rawTargets)
            } catch (e: Exception) {
                logger.warn("启动外部应用失败：${app.name}", e)
                notifyFailure(project, "启动 ${app.text} 失败：${e.message}")
            }
        }
    }

    /**
     * 用“配置页里指定的应用”异步打开一批文件。
     *
     * <p>与 [launchAsync] 的区别：这里的目标应用是用户在设置里填写的 .app 路径或应用名，
     * 不是 [ExternalApp] 枚举，因此统一走 `open -a &lt;应用&gt; &lt;文件&gt;`。
     *
     * @param appReference .app 绝对路径，或应用名（例如 `Typora`）
     * @param paths        要打开的文件路径
     */
    fun launchWithAppAsync(project: Project?, appReference: String, paths: List<String>) {
        if (appReference.isBlank() || paths.isEmpty()) {
            return
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val failed = paths.distinct().filterNot { openWithApp(appReference, it) }
                if (failed.isNotEmpty()) {
                    logger.warn("外置应用启动失败：app=$appReference paths=$failed")
                    notifyFailure(
                        project,
                        "启动 ${AppInstallerDetector.appDisplayName(appReference)} 失败，请确认应用已安装：${failed.first()}",
                    )
                }
            } catch (e: Exception) {
                logger.warn("启动外置应用异常：$appReference", e)
                notifyFailure(project, "启动 ${AppInstallerDetector.appDisplayName(appReference)} 失败：${e.message}")
            }
        }
    }

    /**
     * 同步启动外部应用，只能由 [launchAsync] 在后台线程调用。
     */
    private fun launch(project: Project?, app: ExternalApp, rawTargets: List<ActionTarget>) {
        val targets = ActionTargetResolver.enrich(rawTargets).filter { it.accepts(app) }
        if (targets.isEmpty()) {
            notifyFailure(project, "无法在 ${app.text} 中打开：当前目标不满足打开条件")
            return
        }
        // 浏览器只打开去重后的第一个远程地址，避免弹出多个标签页
        val paths = targets.mapNotNull { it.dedupeKey(app) }.distinct()
        val effectivePaths = if (app.openMode == OpenMode.URL) paths.take(1) else paths
        val failedPaths = effectivePaths.filterNot { open(project, app, it) }
        if (failedPaths.isNotEmpty()) {
            logger.warn("外部应用启动失败：${app.name} paths=$failedPaths")
            notifyFailure(project, "启动 ${app.text} 失败，请确认应用已安装：${failedPaths.first()}")
        }
    }

    /**
     * 用指定外部应用打开一个路径。
     *
     * @return 是否成功
     */
    private fun open(project: Project?, app: ExternalApp, path: String): Boolean {
        if (app.openMode == OpenMode.URL) {
            // 把插件实际生成的地址显示出来：浏览器可能因 HSTS / HTTPS-First 把 http 再次升级成 https，
            // 有了这条反馈就能直接区分「规则没命中」和「浏览器自己升级了协议」。
            logger.info("Git 远程仓库最终打开地址：$path")
            Utils.showNotificationAnActionId(project, NOTIFICATION_TITLE, "打开远程地址：$path")
            return runCatching { BrowserUtil.browse(path) }
                .onFailure { logger.warn("浏览器打开失败：$path", it) }
                .isSuccess
        }
        if (app.openMode == OpenMode.REVEAL) {
            return revealInFinder(path)
        }
        for (bundleId in app.bundleIds) {
            if (runProcess(listOf(OPEN_COMMAND, "-b", bundleId, path))) {
                return true
            }
        }
        for (template in app.cliFallbacks) {
            val executable = AppInstallerDetector.resolveExecutable(template.first()) ?: continue
            val command = template
                .map { if (it == ExternalApp.PATH_PLACEHOLDER) path else it }
                .toMutableList()
            command[0] = executable.absolutePath
            if (runProcess(command, workingDirectory = path.takeIf { File(it).isDirectory })) {
                return true
            }
        }
        return false
    }

    /**
     * 在访达中定位路径。
     *
     * <p>目录：`open <目录>` 直接打开；文件：`open -R <文件>` 打开所在目录并选中该文件。
     * 变更窗口里“已删除的文件”在磁盘上已不存在，此时退化为打开它原来的父目录
     * （父目录也可能被删掉，那就再往上一层），保证按钮不会毫无反应。
     */
    private fun revealInFinder(path: String): Boolean {
        val target = File(path)
        if (target.isDirectory) {
            return runProcess(listOf(OPEN_COMMAND, target.absolutePath))
        }
        if (target.exists()) {
            return runProcess(listOf(OPEN_COMMAND, "-R", target.absolutePath))
        }
        var parent = target.parentFile
        while (parent != null && !parent.isDirectory) {
            parent = parent.parentFile
        }
        if (parent == null) {
            return false
        }
        logger.info("路径已不存在，改为定位其父目录：$path -> ${parent.absolutePath}")
        return runProcess(listOf(OPEN_COMMAND, parent.absolutePath))
    }

    /**
     * 用指定应用（.app 路径或应用名）打开一个文件。
     */
    private fun openWithApp(appReference: String, path: String): Boolean =
        runProcess(listOf(OPEN_COMMAND, "-a", appReference, path))

    /**
     * 执行一条外部命令，读取输出并在超时后强制结束，避免后台线程被卡死。
     */
    private fun runProcess(command: List<String>, workingDirectory: String? = null): Boolean {
        val printable = command.joinToString(" ")
        return try {
            val builder = ProcessBuilder(command).redirectErrorStream(true)
            workingDirectory?.let { dir -> File(dir).takeIf { it.isDirectory }?.let { builder.directory(it) } }
            val process = builder.start()
            if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                logger.warn("命令执行超时：$printable")
                return false
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val exitCode = process.exitValue()
            if (exitCode == 0) {
                logger.info("命令执行成功：$printable")
                true
            } else {
                logger.warn("命令执行失败（exit=$exitCode）：$printable 输出=$output")
                false
            }
        } catch (e: Exception) {
            logger.warn("命令执行异常：$printable", e)
            false
        }
    }

    private fun notifyFailure(project: Project?, content: String) {
        Utils.showNotificationAnActionId(project, NOTIFICATION_TITLE, content)
    }
}
