package wang.dekun.tool.kit.context

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.VcsLogDataKeys
import wang.dekun.tool.kit.repo.GitRepositoryLocator
import java.io.File


/**
 * 上下文解析器：从 [AnActionEvent] 的数据来源按优先级解析出 [ActionTarget]。
 *
 * <p>**完全不依赖 `event.place`**，只按数据来源优先级解析，因此欢迎界面、项目树、
 * 变更窗口、提交窗口、Git Log 五个位置共用同一套逻辑：
 * <ol>
 *     <li>已勾选的变更（[VcsDataKeys.SELECTED_CHANGES] / [VcsDataKeys.CHANGES_SELECTION]）——
 *         覆盖 Git 变更工具窗与模态提交窗口；</li>
 *     <li>Git Log（[VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION] / [VcsLogDataKeys.VCS_LOG]）——
 *         Git Log 右键菜单里没有 VIRTUAL_FILE_ARRAY，只能从日志数据里取仓库根；</li>
 *     <li>项目树 / 编辑器（[CommonDataKeys.VIRTUAL_FILE_ARRAY] / [CommonDataKeys.VIRTUAL_FILE]）；</li>
 *     <li>欢迎界面最近项目（平台未公开的 `RECENT_PROJECT_SELECTED_ITEM`，反射读取并兜底）。</li>
 * </ol>
 *
 * @author WangDeKun
 * <p>
 * Email :  wangdekunemail@gmail.com
 *
 */
object ActionTargetResolver {

    /** 欢迎界面“最近项目”选中项的数据 key，平台没有公开常量，只能按名称取 */
    private const val RECENT_PROJECT_SELECTED_ITEM = "RECENT_PROJECT_SELECTED_ITEM"

    /** 欢迎界面最近项目实现类的简单类名；用“后缀匹配”以兼容 ProviderRecentProjectItem 等子类 */
    private const val RECENT_PROJECT_ITEM_CLASS_NAME = "RecentProjectItem"

    /** 最近项目实现类里存放项目路径的字段名 */
    private const val RECENT_PROJECT_PATH_FIELD = "projectPath"

    /**
     * 完整解析：读取数据来源并补齐 git 信息。
     *
     * <p>只做 DataKey 读取与文件系统探测（不碰 VcsRepositoryManager、不起子进程），
     * 因此可以在 `update()` 的读锁内安全调用。
     */
    fun resolve(event: AnActionEvent): List<ActionTarget> = enrich(resolveRaw(event))

    /**
     * 只读取 DataKey 得到“原始目标”（不含 git 信息），可安全在 EDT 上调用。
     */
    fun resolveRaw(event: AnActionEvent): List<ActionTarget> {
        resolveFromChanges(event)?.let { return it }
        resolveFromLog(event)?.let { return it }
        resolveFromVirtualFiles(event)?.let { return it }
        resolveFromRecentProject(event)?.let { return it }
        return emptyList()
    }

    /**
     * 为原始目标补齐仓库根与远程地址，并按 [ActionTarget.preferRepositoryRoot] 修正目录路径。
     *
     * <p>仓库信息来自 [GitRepositoryLocator]（纯文件系统 + 缓存），不做 git 子进程调用。
     */
    fun enrich(rawTargets: List<ActionTarget>): List<ActionTarget> {
        if (rawTargets.isEmpty()) {
            return emptyList()
        }
        return rawTargets
            .map { raw ->
                val info = GitRepositoryLocator.locate(raw.filePath ?: raw.dirPath)
                raw.copy(
                    dirPath = if (raw.preferRepositoryRoot) info.root ?: raw.dirPath else raw.dirPath,
                    repoRoot = info.root,
                    remoteUrl = GitRepositoryLocator.toBrowserUrl(info.remoteUrl),
                )
            }
            .distinctBy { "${it.dirPath}|${it.filePath}|${it.remoteUrl}" }
    }

    // <editor-fold desc="数据来源解析">

    /** 变更窗口 / 提交窗口：已勾选或已选中的变更 */
    private fun resolveFromChanges(event: AnActionEvent): List<ActionTarget>? {
        val changes: List<Change> = event.getData(VcsDataKeys.SELECTED_CHANGES)?.toList()
            ?: event.getData(VcsDataKeys.CHANGES_SELECTION)?.list
            ?: return null
        if (changes.isEmpty()) {
            return null
        }
        return changes.mapNotNull { changeToTarget(it) }.takeIf { it.isNotEmpty() }
    }

    /** Git Log：优先取选中提交所属的仓库根，其次取日志覆盖的所有仓库根 */
    private fun resolveFromLog(event: AnActionEvent): List<ActionTarget>? {
        val selectedRoots = event.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION)
            ?.commits
            ?.mapNotNull { it.root }
            ?.distinctBy { it.path }
        if (!selectedRoots.isNullOrEmpty()) {
            return selectedRoots.map { repositoryRootTarget(it) }
        }
        val log = event.getData(VcsLogDataKeys.VCS_LOG) ?: return null
        val roots = log.logProviders.keys.distinctBy { it.path }
        if (roots.isEmpty()) {
            return null
        }
        return roots.map { repositoryRootTarget(it) }
    }

    /** 项目树 / 编辑器：选中的文件或目录（支持多选） */
    private fun resolveFromVirtualFiles(event: AnActionEvent): List<ActionTarget>? {
        val files = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.toList()
            ?: listOfNotNull(
                event.getData(CommonDataKeys.VIRTUAL_FILE)
                    ?: event.getData(CommonDataKeys.PSI_FILE)?.virtualFile,
            )
        if (files.isEmpty()) {
            return null
        }
        return files.mapNotNull { virtualFileToTarget(it) }.takeIf { it.isNotEmpty() }
    }

    /** 欢迎界面：最近项目列表里选中的项目 */
    private fun resolveFromRecentProject(event: AnActionEvent): List<ActionTarget>? {
        val selectedItem = event.getData(DataKey.create<Any>(RECENT_PROJECT_SELECTED_ITEM)) ?: return null
        // 用后缀匹配而不是精确相等：平台的实现类可能有 RecentProjectItem /
        // ProviderRecentProjectItem（远程开发、Space 等项目）等多个子类，
        // 精确相等会让后者的右键菜单整体消失。
        if (!selectedItem.javaClass.simpleName.endsWith(RECENT_PROJECT_ITEM_CLASS_NAME)) {
            return null
        }
        val projectPath = readRecentProjectPath(selectedItem) ?: return null
        return listOf(ActionTarget(dirPath = projectPath))
    }

    // </editor-fold>

    // <editor-fold desc="目标构造">

    private fun changeToTarget(change: Change): ActionTarget? {
        // 删除的文件在 VFS 里已经没有可用的 VirtualFile，afterRevision 也是 null，
        // 因此统一回退到 revision 上的路径字符串：
        //   1. 覆盖“变更窗口里删除的文件”这一场景（否则该文件的右键动作会整体消失）；
        //   2. 整个方法不访问 VFS —— resolveRaw 会在 EDT 上被 actionPerformed 调用。
        val path: String = change.virtualFile?.path
            ?: change.afterRevision?.file?.path
            ?: change.beforeRevision?.file?.path
            ?: return null
        return ActionTarget(
            dirPath = File(path).parent.orEmpty(),
            filePath = path,
            preferRepositoryRoot = true,
        )
    }

    private fun virtualFileToTarget(virtualFile: VirtualFile): ActionTarget =
        toTarget(virtualFile, preferRepositoryRoot = false)

    private fun repositoryRootTarget(root: VirtualFile): ActionTarget =
        ActionTarget(dirPath = root.path, preferRepositoryRoot = true)

    private fun toTarget(virtualFile: VirtualFile, preferRepositoryRoot: Boolean): ActionTarget {
        val isDirectory = virtualFile.isDirectory
        val dirPath = when {
            isDirectory -> virtualFile.path
            else -> virtualFile.parent?.path ?: virtualFile.path
        }
        return ActionTarget(
            dirPath = dirPath,
            filePath = virtualFile.path.takeUnless { isDirectory },
            preferRepositoryRoot = preferRepositoryRoot,
        )
    }

    /**
     * 反射读取最近项目的 `projectPath`。该字段是平台私有实现细节，读取失败时静默降级。
     */
    private fun readRecentProjectPath(selectedItem: Any): String? {
        val byField = runCatching {
            selectedItem.javaClass.getField(RECENT_PROJECT_PATH_FIELD)[selectedItem] as? String
        }.getOrNull()
        if (!byField.isNullOrBlank()) {
            return byField
        }
        return runCatching {
            selectedItem.javaClass.getMethod("getProjectPath").invoke(selectedItem) as? String
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    // </editor-fold>
}
