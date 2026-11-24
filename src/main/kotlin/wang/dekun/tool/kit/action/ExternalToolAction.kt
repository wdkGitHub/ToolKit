package wang.dekun.tool.kit.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import wang.dekun.tool.kit.app.AppInstallerDetector
import wang.dekun.tool.kit.app.AppLauncher
import wang.dekun.tool.kit.app.ExternalApp
import wang.dekun.tool.kit.context.ActionTargetResolver
import wang.dekun.tool.kit.utils.Utils


/**
 * 所有“用外部应用打开”动作的统一基类。
 *
 * <p>子类只做参数化（`class ForkAction : ExternalToolAction(ExternalApp.FORK)`），
 * 文字、图标、可见性、可用性、启动逻辑全部在这里统一处理，因此：
 * <ul>
 *     <li>欢迎界面 / 项目树 / 变更窗口 / 提交窗口 / Git Log 五个位置共用同一套逻辑；</li>
 *     <li>不存在任何 `event.place` 硬编码分支；</li>
 *     <li>`update()` 在 BGT 上执行（需要读 VFS 与 git 数据），`actionPerformed()` 在 EDT 上只读 DataKey，
 *         真正的解析与启动都丢到后台线程，不会阻塞 EDT。</li>
 * </ul>
 *
 * @param app 目标外部应用
 *
 * @author WangDeKun
 * <p>
 * Email :  wangdekunemail@gmail.com
 *
 */
open class ExternalToolAction(private val app : ExternalApp ) :
    AnAction(app.text, DEFAULT_DESCRIPTION, app.icon) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    /**
     * 可见性 / 可用性完全由“当前选中了什么”决定：
     * <ol>
     *     <li>选不出任何目标（空编辑器、欢迎界面未选中项目）→ 隐藏；</li>
     *     <li>应用未安装 → 隐藏（不留死按钮）；</li>
     *     <li>应用只针对特定后缀，而选中的不是该类型文件 → 隐藏；</li>
     *     <li>类型匹配但条件不满足（非 git 仓库、仓库无 remote）→ **显示但置灰**，
     *         并在描述里说明原因，避免菜单项随选中项忽隐忽现。</li>
     * </ol>
     */
    override fun update(event: AnActionEvent) {
        val presentation = event.presentation
        presentation.text = app.text
        val targets = ActionTargetResolver.resolve(event)
        if (targets.isEmpty() || !AppInstallerDetector.isAvailable(app)) {
            hide(presentation)
            return
        }
        if (app.hasFileTypeLimit && targets.none { it.fileTypeMatches(app) }) {
            hide(presentation)
            return
        }
        val usableTarget = targets.firstOrNull { it.accepts(app) }
        presentation.isVisible = true
        presentation.isEnabled = usableTarget != null
        presentation.description = usableTarget?.let { DEFAULT_DESCRIPTION }
            ?: targets.firstNotNullOfOrNull { it.rejectionReason(app) }
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project
        // actionPerformed 在 EDT 上执行：这里只读 DataKey，不查询 git 信息
        val rawTargets = ActionTargetResolver.resolveRaw(event)
        if (rawTargets.isEmpty()) {
            Utils.showNotificationAnActionId(project, "ToolKit", "没有可打开的目标：${app.text}")
            return
        }
        AppLauncher.launchAsync(project, app, rawTargets)
    }

    private fun hide(presentation: Presentation) {
        presentation.isVisible = false
        presentation.isEnabled = false
    }

    companion object {

        /** 动作的默认描述 */
        const val DEFAULT_DESCRIPTION = "用外部应用打开当前选中的目录/仓库"
    }
}
