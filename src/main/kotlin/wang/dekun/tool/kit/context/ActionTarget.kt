package wang.dekun.tool.kit.context

import wang.dekun.tool.kit.app.ExternalApp
import wang.dekun.tool.kit.app.OpenMode


/**
 * 一次右键操作解析出的目标上下文。
 *
 * <p>所有可见性判断与启动参数都从这一个对象推导，动作里不再出现任何 `event.place` 分支。
 *
 * @param dirPath              目录路径：选中目录本身，或选中文件所在目录；需要仓库根时会被修正为仓库根
 * @param filePath             选中的具体文件路径（选中目录时为 null）
 * @param repoRoot             所属 git 仓库根目录，不在仓库内时为 null
 * @param remoteUrl            仓库远程地址（已转换成浏览器可访问形式，默认 https；命中域名/IP 规则时按配置的协议与端口生成）
 * @param preferRepositoryRoot 是否应当把 [dirPath] 修正为仓库根（变更窗口 / Git Log 场景）
 *
 * @author WangDeKun
 * <p>
 * Email :  wangdekunemail@gmail.com
 *
 */
data class ActionTarget(
    val dirPath: String,
    val filePath: String? = null,
    val repoRoot: String? = null,
    val remoteUrl: String? = null,
    val preferRepositoryRoot: Boolean = false,
) {

    /**
     * 该目标是否满足外部应用的可见条件。
     */
    fun accepts(app: ExternalApp): Boolean = rejectionReason(app) == null

    /**
     * 目标类型是否匹配（只判断“是不是这种文件”，不判断仓库、远程地址等条件）。
     *
     * <p>用于区分两种情况：
     * <ul>
     *     <li>类型不匹配（例如在普通文件夹上调用“用 Typora 打开”）——动作应当**隐藏**；</li>
     *     <li>类型匹配但条件不满足（例如在非 git 文件夹上调用 Fork）——动作应当**置灰**，
     *         让用户看到“这里不能这么做”，而不是让菜单项忽隐忽现。</li>
     * </ul>
     */
    fun fileTypeMatches(app: ExternalApp): Boolean {
        val extension = app.fileExtension ?: return true
        val file = filePath ?: return false
        return file.endsWith(".$extension", ignoreCase = true)
    }

    /**
     * 不满足条件时的原因，用于动作的置灰提示；满足条件时返回 null。
     */
    fun rejectionReason(app: ExternalApp): String? {
        if (!fileTypeMatches(app)) {
            return "当前选中的不是 .${app.fileExtension} 文件"
        }
        if (app.requiresRepository && repoRoot.isNullOrBlank()) {
            return "当前选中的内容不在 git 仓库中"
        }
        if (app.requiresRemoteUrl && remoteUrl.isNullOrBlank()) {
            return "当前仓库没有配置远程地址（remote）"
        }
        if (targetPath(app).isNullOrBlank()) {
            return "当前选中项没有可打开的路径"
        }
        return null
    }

    /**
     * 计算该应用实际要打开的路径。
     */
    fun targetPath(app: ExternalApp): String? = when (app.openMode) {
        OpenMode.DIRECTORY -> if (app.requiresRepository) repoRoot ?: dirPath else dirPath
        OpenMode.FILE -> filePath
        OpenMode.URL -> remoteUrl
        OpenMode.REVEAL -> filePath ?: dirPath
    }

    /** 去重键：同一个仓库/目录/文件/地址只处理一次 */
    fun dedupeKey(app: ExternalApp): String? = targetPath(app)
}
