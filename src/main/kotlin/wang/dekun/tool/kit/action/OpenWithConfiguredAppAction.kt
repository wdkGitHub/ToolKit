package wang.dekun.tool.kit.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import wang.dekun.tool.kit.app.AppInstallerDetector
import wang.dekun.tool.kit.app.AppLauncher
import wang.dekun.tool.kit.context.ActionTargetResolver
import wang.dekun.tool.kit.settings.ExternalAppSettings
import wang.dekun.tool.kit.utils.Utils


/**
 * 按设置页里的“后缀 → 外置应用”映射打开选中的文件。
 *
 * <p>这是原“用 Typora 打开 markdown”的通用化版本：菜单文字随配置动态变化
 * （`Open With Typora` / `Open With Visual Studio Code` …），且只在选中“配置过的后缀”
 * 的文件时出现，因此不会出现点了没反应的菜单项。
 *
 * <p>action id：`blog.dekun.wang.OpenWithApp`。
 *
 * @author WangDeKun
 * <p>
 * Email :  wangdekunemail@gmail.com
 *
 */
class OpenWithConfiguredAppAction : AnAction("Open With", "用设置里为该后缀配置的外置应用打开选中的文件", AllIcons.Actions.MenuOpen) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val presentation = event.presentation
        val bindings = resolveBindings(event)
        // 选中的文件都没有配置外置应用（或者选的是目录）→ 整个菜单项不出现
        if (bindings.isEmpty()) {
            presentation.isVisible = false
            presentation.isEnabled = false
            return
        }
        val apps = bindings.map { it.app }.distinct()
        val usableApps = apps.filter { AppInstallerDetector.isAppUsable(it) }
        presentation.text = if (apps.size == 1) {
            "Open With ${AppInstallerDetector.appDisplayName(apps.first())}"
        } else {
            "Open With …"
        }
        presentation.isVisible = true
        presentation.isEnabled = usableApps.isNotEmpty()
        presentation.description = if (usableApps.isEmpty()) {
            "配置的应用不存在，请到 设置 → 工具 → 外部应用打开方式 检查"
        } else {
            "用配置的外置应用打开选中的文件"
        }
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project
        val bindings = resolveBindings(event).filter { AppInstallerDetector.isAppUsable(it.app) }
        if (bindings.isEmpty()) {
            Utils.showNotificationAnActionId(
                project,
                "ToolKit",
                "没有可用的外置应用，请到 设置 → 工具 → 外部应用打开方式 配置",
            )
            return
        }
        bindings.forEach { binding ->
            AppLauncher.launchWithAppAsync(project, binding.app, binding.files)
        }
    }

    /**
     * 把选中的文件按“配置的应用”分组；只依赖 DataKey，不访问 git / 文件系统。
     */
    private fun resolveBindings(event: AnActionEvent): List<Binding> {
        val settings = ExternalAppSettings.getInstance()
        val grouped = LinkedHashMap<String, MutableList<String>>()
        ActionTargetResolver.resolveRaw(event).forEach { target ->
            val filePath = target.filePath ?: return@forEach
            val app = settings.appFor(filePath) ?: return@forEach
            grouped.getOrPut(app) { mutableListOf() }.add(filePath)
        }
        return grouped.map { Binding(it.key, it.value) }
    }

    /**
     * 一个应用对应的一组文件。
     */
    private data class Binding(val app: String, val files: List<String>)
}
