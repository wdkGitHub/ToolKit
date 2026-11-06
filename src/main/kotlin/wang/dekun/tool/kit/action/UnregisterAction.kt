package wang.dekun.tool.kit.action

import  wang.dekun.tool.kit.action.base.BaseAnAction
import  wang.dekun.tool.kit.ui.UnregisterActionConfigurable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil


/**
 *
 * @author WangDeKun
 * <p>
 * Email :  wangdekunemail@gmail.com
 *
 */


class UnregisterAction : BaseAnAction() {

    override fun update(event: AnActionEvent) {
        event.project ?: { event.presentation.isEnabledAndVisible = false }
        event.presentation.text = "注销AnAction"
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        ShowSettingsUtil.getInstance().editConfigurable(project, UnregisterActionConfigurable(project))
    }
}